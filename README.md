# Zorvyn Finance Backend

A production-grade Spring Boot 3 backend for a finance dashboard system with role-based access control, JWT authentication with refresh token rotation, Redis caching, audit logging via AOP, anomaly detection, rate limiting, spending forecasts, budget envelopes, merchant intelligence, velocity scoring, DNA fingerprinting, and CSV/Excel export.

---

## Live Deployment

| Service | URL |
|---|---|
| Frontend | https://zorvyn-frontend-53m1.onrender.com/login |
| Backend API | https://zorvyn-backend-tyi6.onrender.com |
| Swagger UI | https://zorvyn-backend-tyi6.onrender.com/swagger-ui/index.html |

### Default Login Credentials

| Email | Password | Role |
|---|---|---|
| `admin@finance.com` | `admin123` | ADMIN |
| `analyst@finance.com` | `analyst123` | ANALYST |
| `viewer@finance.com` | `viewer123` | VIEWER |

> **Note:** The free Render tier spins down after 15 minutes of inactivity. The first request after a cold start may take 30–60 seconds.

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                     React Frontend                       │
│              (Vite + React Router + Recharts)            │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP / REST / SSE
┌────────────────────────▼────────────────────────────────┐
│                  Spring Boot 3 API                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐  │
│  │Controller│→ │ Service  │→ │   Repo   │→ │  JPA   │  │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘  │
│       ↑              ↑                                   │
│  JWT Filter     AOP Audit                                │
│  Rate Limit     @PreAuthorize                            │
└──────────┬──────────────────────────┬───────────────────┘
           │                          │
    ┌──────▼──────┐           ┌───────▼──────┐
    │ PostgreSQL  │           │    Redis      │
    │  (Render)   │           │  (Upstash)    │
    └─────────────┘           └──────────────┘
```

The application follows a strict layered architecture:
- **Controllers** handle HTTP concerns only — no business logic
- **Services** own all business rules and are secured with `@PreAuthorize`
- **Repositories** are pure data access via Spring Data JPA
- **DTOs** decouple the API contract from the domain model — entities are never serialized directly
- **Cross-cutting concerns** (audit logging, rate limiting) are handled via AOP and filters, keeping business logic clean

---

## 2. Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Framework | Spring Boot 3.2 + Spring Security 6 | Industry standard, mature ecosystem, excellent Spring Data integration |
| Database | PostgreSQL 16 via Spring Data JPA + Hibernate | ACID compliance, strong JSON support, production-proven |
| Migrations | Flyway | Versioned, repeatable schema changes tracked in source control |
| Cache | Redis via Spring Cache (Upstash) | Sub-millisecond reads for dashboard aggregations; TTL-based invalidation |
| Auth | JWT (JJWT 0.12) + Refresh Token Rotation | Stateless auth with replay attack prevention |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) | Auto-generated from code, always in sync |
| Export | Apache POI (Excel) + OpenCSV | Industry-standard libraries for office format generation |
| Rate Limiting | Bucket4j | Token bucket algorithm, role-aware limits, no external dependency needed |
| Build | Maven | Stable, widely supported in CI/CD pipelines |
| Deployment | Docker + Render + Upstash | Reproducible environments, single-command startup |

---

## 3. Cloud Deployment Guide

### Infrastructure

| Service | Provider | Plan |
|---|---|---|
| Backend (Spring Boot) | Render | Free (Docker) |
| Frontend (React/Vite) | Render | Free (Docker) |
| PostgreSQL | Render | Free managed Postgres |
| Redis | Upstash | Free (TLS, 256MB) |

### Step 1 — Create Upstash Redis

1. Sign up at [upstash.com](https://upstash.com)
2. Create a new Redis database — select the **Singapore** region to match Render's Postgres
3. Enable **TLS**
4. Copy the **Redis URL** — it looks like `rediss://default:<password>@<host>.upstash.io:6379`

### Step 2 — Deploy via Render Blueprint

The `render.yaml` at the repo root defines all three services. Push the repo to GitHub, then:

1. Go to [render.com](https://render.com) → **New** → **Blueprint**
2. Connect your GitHub repo
3. Render will detect `render.yaml` and create:
   - `zorvyn-backend` (Docker web service)
   - `zorvyn-frontend` (Docker web service)
   - `zorvyn-postgres` (managed PostgreSQL)

### Step 3 — Set Environment Variables

After the blueprint creates the services, set these manually in the Render dashboard:

**zorvyn-backend** → Environment:

| Key | Value |
|---|---|
| `SPRING_DATA_REDIS_URL` | `rediss://default:<password>@<host>.upstash.io:6379` |
| `SPRING_DATA_REDIS_SSL` | `true` |
| `JWT_SECRET` | Any random string ≥ 32 chars (run `openssl rand -base64 32`) |
| `FRONTEND_URL` | `https://zorvyn-frontend-<slug>.onrender.com` |
| `SPRING_DATASOURCE_URL` | Use the **external** Postgres URL from Render (see note below) |

> **Important:** Render's `connectionString` property injects the internal hostname (`dpg-xxx`), which is only reachable from services in the same region. If your backend is in a different region than the Postgres instance, override `SPRING_DATASOURCE_URL` manually with the **External Database URL** from the Postgres service's Connect tab (the one ending in `.singapore-postgres.render.com`).

**zorvyn-frontend** → Environment:

| Key | Value |
|---|---|
| `VITE_API_URL` | `https://zorvyn-backend-<slug>.onrender.com` |

> After setting `VITE_API_URL`, trigger a **Manual Deploy** on the frontend — Vite bakes this value in at build time, so a redeploy is required.

### Step 4 — Fix Admin Password (first deploy only)

Flyway seeds the admin user on first startup. If the password hash doesn't match, run this against your Postgres external URL:

```bash
# Linux/macOS
PGPASSWORD=<db-password> psql -h <external-host> -U <db-user> <db-name> -c \
  "UPDATE users SET password_hash = '\$2a\$10\$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO' WHERE email = 'admin@finance.com';"

# Windows (PowerShell) — use the full psql path if needed
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" "postgresql://<user>:<password>@<external-host>:5432/<db>" -c "UPDATE users SET password_hash = '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO' WHERE email = 'admin@finance.com';"
```

Or use the **PSQL Command** button in the Render Postgres dashboard (browser-based shell, no installation needed).

### render.yaml Reference

```yaml
services:
  - type: web
    name: zorvyn-backend
    runtime: docker
    rootDir: backend
    dockerfilePath: ./Dockerfile
    plan: free
    envVars:
      - key: SPRING_DATASOURCE_URL
        fromDatabase:
          name: zorvyn-postgres
          property: connectionString
      - key: SPRING_DATASOURCE_USERNAME
        fromDatabase:
          name: zorvyn-postgres
          property: user
      - key: SPRING_DATASOURCE_PASSWORD
        fromDatabase:
          name: zorvyn-postgres
          property: password
      - key: SPRING_DATA_REDIS_URL
        sync: false          # set manually from Upstash (rediss://...)
      - key: SPRING_DATA_REDIS_SSL
        value: "true"
      - key: JWT_SECRET
        sync: false          # openssl rand -base64 32
      - key: FRONTEND_URL
        sync: false          # set after frontend deploys

  - type: web
    name: zorvyn-frontend
    runtime: docker
    rootDir: frontend
    dockerfilePath: ./Dockerfile
    plan: free
    envVars:
      - key: VITE_API_URL
        sync: false          # set to https://zorvyn-backend-<slug>.onrender.com

databases:
  - name: zorvyn-postgres
    plan: free
    databaseName: financedb
```

---

## 4. How the Application Works — Full Walkthrough

### 4.1 Authentication Flow

When a user calls `POST /api/v1/auth/login`:
1. `AuthController` receives the request and delegates to `AuthServiceImpl`
2. `AuthServiceImpl` calls Spring's `AuthenticationManager`, which internally calls `UserDetailsServiceImpl`
3. `UserDetailsServiceImpl` loads the user from PostgreSQL, checks `is_active`, and builds a `UserDetails` object with `ROLE_` prefixed authorities
4. If authentication passes, an **access token** (JWT, 15 min TTL) and a **refresh token** (UUID, 7 day TTL) are issued
5. The refresh token is hashed and stored in the `refresh_tokens` table — never stored in plaintext
6. On every `POST /auth/refresh`, the old token is revoked and a new pair is issued (rotation prevents replay attacks)

Every subsequent request passes through `JwtAuthFilter`, which:
- Extracts the Bearer token from the `Authorization` header
- Validates signature and expiry using `JwtUtils`
- Sets the `SecurityContext` so `@PreAuthorize` and `@AuthenticationPrincipal` work downstream

### 4.2 Role-Based Access Control

Three roles exist: `VIEWER`, `ANALYST`, `ADMIN`.

- Role checks are enforced at the **service layer** via `@PreAuthorize`, not just controllers
- New users registered via `/auth/register` get `VIEWER` by default
- Admins can upgrade roles via `PUT /api/v1/users/{id}`

| Action | Minimum Role |
|---|---|
| Read transactions, dashboard, export | VIEWER |
| Create, update transactions | ANALYST |
| Delete, restore, manage users, budgets | ADMIN |

### 4.3 Transaction Lifecycle

When `POST /api/v1/transactions` is called:

1. **Idempotency check** — duplicate `Idempotency-Key` returns the original response
2. Transaction saved to PostgreSQL
3. **Anomaly detection** — amount > 3σ from category average adds `X-Anomaly-Warning` header
4. **Off-hours detection** — transactions between 1am–5am set `offHours: true`
5. **DNA fingerprinting** — SHA-256 hash of `(userId | amount | categoryId | 4-hour bucket)` flags duplicates within 5 minutes
6. **Velocity scoring** — EMA score (0–100) comparing today's spend to 7-day rolling average, returned in `X-Velocity-Score`
7. **Merchant tagging** — notes scanned for known merchant keywords; `MerchantTag` record saved
8. **SSE broadcast** — new transaction pushed to all connected clients
9. **Cache eviction** — `dashboard-summary` Redis cache evicted
10. **Audit log** — `AuditAspect` asynchronously writes an `AuditLog` record

### 4.4 Dashboard & Caching

`GET /api/v1/dashboard/summary` returns total income, expenses, net balance, category breakdown, and 10 recent transactions. Cached in Redis with 5-minute TTL via `@Cacheable("dashboard-summary")`.

### 4.5 Budget Envelopes

Admins set monthly spending limits per category via `POST /api/v1/budgets`. `BudgetService.getStatus()` calculates actual spend, percentage used, projected end-of-month spend, and flags envelopes over 80%.

### 4.6 Spending Forecast

`GET /api/v1/forecast?days=30` uses exponential smoothing (α=0.3) on the last 30 days of daily expense data per category to project future spend.

### 4.7 Merchant Intelligence

`GET /api/v1/merchants/top?period=monthly` aggregates `MerchantTag` records to return top merchants by total spend.

### 4.8 Velocity Scoring & Risk Profiles

On every transaction creation, `VelocityService` computes a velocity score (0–100) using EMA (α=0.4). Admins view full risk profiles via `GET /api/v1/users/{id}/risk-profile`.

### 4.9 Rate Limiting

`RateLimitFilter` uses Bucket4j's token bucket algorithm with per-user in-memory buckets:

| Role | Limit |
|---|---|
| VIEWER | 100 requests/minute |
| ANALYST | 200 requests/minute |
| ADMIN | 500 requests/minute |

### 4.10 Transaction Simulator

`TransactionSimulator` fires every **1 hour** and creates a realistic random transaction for a random active user, exercising the full transaction pipeline including anomaly detection, velocity scoring, merchant tagging, SSE broadcast, and audit logging.

---

## 5. Local Setup

### Option A — Docker (recommended)

```bash
cd "path/to/Zorvyn_Backend_Assessment_Anagha_Badhe"
docker-compose up --build
```

API: `http://localhost:8080` | Swagger: `http://localhost:8080/swagger-ui.html`

After first startup, fix the admin password:
```bash
docker exec -it zorvyn_backend_assessment_anagha_badhe-postgres-1 psql -U postgres -d financedb -c "UPDATE users SET password_hash = '\$2a\$10\$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO' WHERE email = 'admin@finance.com';"
```

### Option B — Local (PostgreSQL + Redis already running)

Prerequisites: PostgreSQL 16 on port 5432 (`financedb` / `postgres` / `postgres`), Redis on port 6379.

```bash
docker-compose up postgres redis
mvn spring-boot:run
```

### Frontend

```bash
cd frontend && npm install && npm run dev
```

Frontend: `http://localhost:3000`

### Clean slate

```bash
docker-compose down -v && docker-compose up --build
```

---

## 6. API Reference

Swagger UI: `https://zorvyn-backend-tyi6.onrender.com/swagger-ui/index.html`

### Authentication — `/api/v1/auth`
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/register` | Public | Register (gets VIEWER role) |
| POST | `/login` | Public | Returns access + refresh tokens |
| POST | `/refresh` | Public | Rotate refresh token |
| POST | `/logout` | Public | Revoke refresh token |
| GET | `/me` | Any role | Current user profile + roles |

### Transactions — `/api/v1/transactions`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/` | VIEWER+ | List with filters |
| GET | `/{id}` | VIEWER+ | Get by ID |
| POST | `/` | ANALYST+ | Create (supports `Idempotency-Key` header) |
| PUT | `/{id}` | ANALYST+ | Update |
| DELETE | `/{id}` | ADMIN | Soft delete |
| GET | `/deleted` | ADMIN | Recycle bin |
| POST | `/{id}/restore` | ADMIN | Restore |
| GET | `/{id}/history` | VIEWER+ | Audit history |
| GET | `/stream` | VIEWER+ | Live SSE feed |
| GET | `/export?format=csv` | VIEWER+ | Export CSV |
| GET | `/export?format=excel` | VIEWER+ | Export Excel |

### Dashboard — `/api/v1/dashboard`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/summary` | VIEWER+ | Income, expenses, net balance, category breakdown (cached 5 min) |
| GET | `/trends` | VIEWER+ | Month-by-month totals for past 12 months |

### Users — `/api/v1/users`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/` | ADMIN | List all users |
| GET | `/{id}` | ADMIN | Get user by ID |
| PUT | `/{id}` | ADMIN | Update name, status, or roles |
| DELETE | `/{id}` | ADMIN | Deactivate user |
| GET | `/{id}/risk-profile` | ADMIN | Velocity score, 24h/7d spend, risk level |

### Budget Envelopes — `/api/v1/budgets`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/` | ADMIN | Create or update monthly budget limit |
| GET | `/status?monthYear=2025-06` | VIEWER+ | Real-time spend vs limit |

### Spending Forecast — `/api/v1/forecast`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/?days=30` | VIEWER+ | Projected spend per category |

### Merchant Intelligence — `/api/v1/merchants`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/top?period=monthly` | VIEWER+ | Top merchants by spend |

### Response Headers on Transaction Creation
| Header | Meaning |
|---|---|
| `X-Anomaly-Warning: true` | Amount > 3σ from category average |
| `X-Anomaly-Detail` | Human-readable explanation |
| `X-Velocity-Score` | Spend velocity score 0–100 |
| `X-RateLimit-Remaining` | Remaining requests in current window |

---

## 7. Running Tests

```bash
mvn test
```

| Test class | Type | What it covers |
|---|---|---|
| `AuthServiceTest` | Unit (Mockito) | Register, login, duplicate email rejection |
| `TransactionServiceTest` | Unit (Mockito) | Create, idempotency, soft delete, restore |
| `TransactionControllerTest` | Integration (MockMvc) | HTTP layer, role enforcement |
| `TransactionRepositoryTest` | Integration (Testcontainers) | Real PostgreSQL queries |

---

## 8. Project Structure

```
src/main/java/com/financeapi/
├── config/       ← SecurityConfig, RedisConfig, OpenApiConfig, RateLimitFilter,
│                   DataInitializer, DataSourceConfig, TransactionSimulator
├── domain/       ← JPA entities: User, Role, Transaction, Category, RefreshToken,
│                   AuditLog, Budget, TransactionDna, MerchantTag
├── repository/   ← Spring Data JPA repositories
├── service/      ← Service interfaces + implementations
│   └── impl/     ← AuthServiceImpl, TransactionServiceImpl, DashboardServiceImpl,
│                   BudgetService, ForecastService, MerchantService,
│                   VelocityService, DnaFingerprintService, SseEmitterRegistry
├── controller/   ← AuthController, TransactionController, DashboardController,
│                   UserController, BudgetController, ForecastController, MerchantController
├── dto/          ← Request/Response DTOs
├── security/     ← JwtUtils, JwtAuthFilter, UserDetailsServiceImpl
├── exception/    ← Custom exceptions + GlobalExceptionHandler
├── audit/        ← AuditAspect (AOP)
└── util/         ← TransactionSpecification

frontend/         ← Vite + React frontend (Zorvyn theme)
render.yaml       ← Render Blueprint (backend + frontend + postgres)
docker-compose.yml← Local development stack
```

---

## 9. Known Limitations & Future Improvements

- **Rate limit state is in-memory**: In a horizontally scaled deployment, buckets should be stored in Redis using Bucket4j's `RedisProxyManager`
- **Refresh token hash is weak**: `hashCode()` is used for simplicity. Production should use SHA-256 with a pepper
- **No email verification**: Registered users are immediately active
- **Render free tier cold starts**: Services spin down after 15 minutes of inactivity; first request takes 30–60 seconds
- **Merchant dictionary is static**: A production system would use a merchant classification API or ML model
- **Category management**: Categories are seeded via Flyway; a `POST /categories` endpoint for admins would be a natural next addition
