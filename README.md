# Zorvyn Finance Backend

A production-grade Spring Boot 3 backend for a finance dashboard system with role-based access control, JWT authentication with refresh token rotation, Redis caching, audit logging via AOP, anomaly detection, rate limiting, spending forecasts, budget envelopes, merchant intelligence, velocity scoring, DNA fingerprinting, and CSV/Excel export.

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
    │  (data)     │           │  (cache +     │
    └─────────────┘           │  rate limit)  │
                              └──────────────┘
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
| Cache | Redis via Spring Cache | Sub-millisecond reads for dashboard aggregations; TTL-based invalidation |
| Auth | JWT (JJWT 0.12) + Refresh Token Rotation | Stateless auth with replay attack prevention |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) | Auto-generated from code, always in sync |
| Export | Apache POI (Excel) + OpenCSV | Industry-standard libraries for office format generation |
| Rate Limiting | Bucket4j | Token bucket algorithm, role-aware limits, no external dependency needed |
| Build | Maven | Stable, widely supported in CI/CD pipelines |
| Deployment | Docker + Docker Compose | Reproducible environments, single-command startup |

---

## 3. How the Application Works — Full Walkthrough

### 3.1 Authentication Flow

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

### 3.2 Role-Based Access Control

Three roles exist: `VIEWER`, `ANALYST`, `ADMIN`.

- Role checks are enforced at the **service layer** via `@PreAuthorize`, not just controllers. This prevents bypasses when one service calls another.
- New users registered via `/auth/register` get `VIEWER` by default
- Admins can upgrade roles via `PUT /api/v1/users/{id}`

| Action | Minimum Role |
|---|---|
| Read transactions, dashboard, export | VIEWER |
| Create, update transactions | ANALYST |
| Delete, restore, manage users, budgets | ADMIN |

### 3.3 Transaction Lifecycle

When `POST /api/v1/transactions` is called:

1. **Idempotency check** — if an `Idempotency-Key` header is present and a matching transaction exists, the original response is returned immediately without creating a duplicate
2. The transaction is saved to PostgreSQL
3. **Anomaly detection** — if the amount deviates more than 3 standard deviations from the user's historical average for that category, `X-Anomaly-Warning: true` and `X-Anomaly-Detail` headers are added to the response
4. **Off-hours detection** — if the transaction is created between 1am–5am in the user's timezone, `offHours: true` is set in the response
5. **DNA fingerprinting** — a SHA-256 hash of `(userId | amount | categoryId | 4-hour time bucket)` is computed. If an identical hash exists within the last 5 minutes, `possibleDuplicate: true` is set in the response
6. **Velocity scoring** — an Exponential Moving Average (EMA) score (0–100) is computed based on how today's spend compares to the 7-day rolling daily average. Returned in the `X-Velocity-Score` header
7. **Merchant tagging** — the transaction notes are scanned for known merchant keywords (Swiggy, Zomato, Amazon, Uber, Netflix, etc.). If matched, a `MerchantTag` record is saved and `merchantName` is populated in the response. Falls back to extracting the first capitalized word sequence
8. **SSE broadcast** — the new transaction is pushed to all connected clients via Server-Sent Events (`GET /transactions/stream`)
9. **Cache eviction** — the `dashboard-summary` Redis cache is evicted so the next dashboard call reflects the new data
10. **Audit log** — `AuditAspect` intercepts the method via AOP and asynchronously writes an `AuditLog` record with the action, entity type, entity ID, and IP address

Soft delete (`DELETE /{id}`) sets `is_deleted = true`. The record is never physically removed. Admins can view deleted records via `GET /transactions/deleted` and restore them via `POST /{id}/restore`.

### 3.4 Dashboard & Caching

`GET /api/v1/dashboard/summary` returns:
- Total income, total expenses, net balance
- Category breakdown (spend per category)
- 10 most recent transactions

This query is expensive (aggregates across all transactions). It is cached in Redis with a 5-minute TTL via `@Cacheable("dashboard-summary")`. Every write operation (create, update, delete, restore) calls `@CacheEvict(allEntries = true)` to prevent stale reads.

`GET /api/v1/dashboard/trends` returns month-by-month income/expense totals for the past 12 months, also cached.

### 3.5 Budget Envelopes

Admins can set monthly spending limits per category via `POST /api/v1/budgets`. The `BudgetService.getStatus()` method:
- Calculates actual spend for the month per category
- Computes percentage used
- Projects end-of-month spend based on the current daily burn rate
- Flags envelopes where spend has exceeded 80% of the limit

### 3.6 Spending Forecast

`GET /api/v1/forecast?days=30` uses **exponential smoothing** (α=0.3) on the last 30 days of daily expense data per category to project future daily and total spend. This is a lightweight time-series forecasting technique that gives more weight to recent data.

### 3.7 Merchant Intelligence

`GET /api/v1/merchants/top?period=monthly` aggregates `MerchantTag` records to return the top merchants by total spend for the current month or last 7 days.

### 3.8 Velocity Scoring & Risk Profiles

On every transaction creation, `VelocityService` computes a **velocity score** (0–100):
- Compares today's total spend to the 7-day rolling daily average
- A spike ratio of 5x or more = score of 100
- The score is smoothed with EMA (α=0.4) against the user's previous score and persisted on the `User` entity

Admins can view a user's full risk profile via `GET /api/v1/users/{id}/risk-profile`, which includes the score, 24h spend, 7d spend, and a `LOW / MEDIUM / HIGH` risk level.

### 3.9 Rate Limiting

`RateLimitFilter` runs after `JwtAuthFilter`. It uses Bucket4j's token bucket algorithm with per-user buckets stored in a `ConcurrentHashMap`:

| Role | Limit |
|---|---|
| VIEWER | 100 requests/minute |
| ANALYST | 200 requests/minute |
| ADMIN | 500 requests/minute |

Exceeded requests return `429 Too Many Requests`. Remaining tokens are returned in the `X-RateLimit-Remaining` header.

### 3.10 Audit Logging

`AuditAspect` is an `@Aspect` that intercepts all write methods on `TransactionServiceImpl` and `UserServiceImpl` using `@AfterReturning` pointcuts. It runs `@Async` so it never adds latency to the main request. Each audit log captures: action, entity type, entity ID, old/new values, IP address (respects `X-Forwarded-For`), and timestamp.

The full audit history of any transaction can be retrieved via `GET /api/v1/transactions/{id}/history`.

### 3.11 Export

`GET /api/v1/transactions/export?format=csv` — generates a CSV using OpenCSV
`GET /api/v1/transactions/export?format=excel` — generates an `.xlsx` file using Apache POI

Both return the file as a downloadable attachment.

### 3.12 Server-Sent Events (Live Feed)

`GET /api/v1/transactions/stream` returns a persistent SSE connection. Every time a new transaction is created, it is broadcast to all connected clients via `SseEmitterRegistry`. This enables a real-time transaction feed in the frontend without polling.

### 3.13 DataInitializer (Admin Password Fix)

On every startup, `DataInitializer` checks whether the seeded admin password hash actually matches `admin123`. If not (e.g. after a fresh DB volume with a wrong hash), it re-encodes and saves the correct hash. This is a no-op on subsequent boots once the hash is correct.

---

## 4. Design Decisions

- **JWT with refresh token rotation**: On every `/auth/refresh` call, the old token is revoked and a new pair is issued. Tokens are stored as hashes (not plaintext) in the DB. This prevents replay attacks if a refresh token is intercepted.

- **`@PreAuthorize` at the service layer, not just controllers**: Role checks in controllers can be bypassed if a service is called from another service. Enforcing at the service layer is the correct pattern.

- **AOP for audit logging**: An `@Aspect` intercepts all write operations on `TransactionService` and `UserService` asynchronously. This keeps audit concerns completely out of business logic.

- **JPA Specifications for dynamic filtering**: Instead of writing N query methods for every filter combination, a single `Specification` builder composes predicates at runtime.

- **Soft deletes with recycle bin**: `is_deleted = true` instead of `DELETE`. Admins can view and restore deleted records.

- **Redis caching with explicit eviction**: `@Cacheable("dashboard-summary")` caches the expensive aggregation query. Every write operation calls `@CacheEvict(allEntries = true)` to prevent stale reads.

- **Anomaly detection via 3-sigma rule**: If the amount deviates more than 3 standard deviations from the user's historical average for that category, anomaly headers are returned.

- **DNA fingerprinting for duplicate detection**: SHA-256 hash of `(userId | amount | categoryId | 4-hour time bucket)`. Duplicate submissions within 5 minutes are flagged without blocking them.

- **Idempotency keys**: `POST /transactions` accepts an optional `Idempotency-Key` header. Duplicate submissions return the original response — the same pattern used by Stripe.

- **Exponential smoothing for forecasting**: α=0.3 gives more weight to recent spend data, making forecasts responsive to behavioral changes without being noisy.

- **Consistent error envelopes**: Every error response includes `timestamp`, `status`, `error`, `message`, and `path`. Validation errors additionally include a `fieldErrors` map.

- **API versioning from day one** (`/api/v1/...`): Allows breaking changes in `/api/v2/` without affecting existing clients.

---

## 5. Assumptions

1. New users registered via `/auth/register` receive the `VIEWER` role by default. Admins can upgrade roles via `PUT /users/{id}`.
2. Categories are seeded at startup (Food, Transport, Salary, Entertainment, Utilities, Healthcare, Other). Category management endpoints are out of scope.
3. Anomaly detection only triggers when there is at least one prior transaction in the same category for that user (requires a baseline to compute mean and stddev).
4. Refresh token hashing uses `hashCode()` for brevity. In production, SHA-256 with a salt would be used.
5. User deactivation (soft delete) prevents future logins via `UserDetailsService` but does not invalidate existing access tokens — they expire naturally within 15 minutes.
6. Rate limiting buckets are stored in-memory (ConcurrentHashMap). In a multi-instance deployment, these would be moved to Redis using Bucket4j's Redis backend.
7. The frontend is a separate Vite/React app in the `frontend/` directory, proxying API calls to `localhost:8080` in development.
8. Merchant tagging uses a keyword dictionary. In production this would be replaced with a proper merchant classification API.

---

## 6. API Reference

Swagger UI (interactive): `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/api-docs`

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
| GET | `/` | VIEWER+ | List with filters: `type`, `categoryId`, `from`, `to`, `page`, `size` |
| GET | `/{id}` | VIEWER+ | Get by ID |
| POST | `/` | ANALYST+ | Create (supports `Idempotency-Key` header) |
| PUT | `/{id}` | ANALYST+ | Update |
| DELETE | `/{id}` | ADMIN | Soft delete |
| GET | `/deleted` | ADMIN | Recycle bin |
| POST | `/{id}/restore` | ADMIN | Restore from recycle bin |
| GET | `/{id}/history` | VIEWER+ | Full audit history of a transaction |
| GET | `/stream` | VIEWER+ | Live transaction feed via SSE |
| GET | `/export?format=csv` | VIEWER+ | Export as CSV |
| GET | `/export?format=excel` | VIEWER+ | Export as Excel |

### Dashboard — `/api/v1/dashboard`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/summary` | VIEWER+ | Income, expenses, net balance, category breakdown, recent 10 (cached 5 min) |
| GET | `/trends` | VIEWER+ | Month-by-month income/expense for past 12 months |

### Users — `/api/v1/users`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/` | ADMIN | List all users |
| GET | `/{id}` | ADMIN | Get user by ID |
| PUT | `/{id}` | ADMIN | Update name, status, or roles |
| DELETE | `/{id}` | ADMIN | Deactivate user (soft delete) |
| GET | `/{id}/risk-profile` | ADMIN | Velocity score, 24h/7d spend, risk level |

### Budget Envelopes — `/api/v1/budgets`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/` | ADMIN | Create or update a monthly budget limit per category |
| GET | `/status?monthYear=2025-06` | VIEWER+ | Real-time spend vs limit with projected overage |

### Spending Forecast — `/api/v1/forecast`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/?days=30` | VIEWER+ | Projected daily and total spend per category using exponential smoothing |

### Merchant Intelligence — `/api/v1/merchants`
| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/top?period=monthly` | VIEWER+ | Top merchants by spend (monthly or weekly) |

### Rate Limits
| Role | Limit |
|---|---|
| VIEWER | 100 requests/minute |
| ANALYST | 200 requests/minute |
| ADMIN | 500 requests/minute |

Exceeded requests return `429 Too Many Requests`. Remaining tokens are returned in the `X-RateLimit-Remaining` header.

### Response Headers on Transaction Creation
| Header | When present | Meaning |
|---|---|---|
| `X-Anomaly-Warning: true` | Amount > 3σ from category average | Unusual spend detected |
| `X-Anomaly-Detail` | Same as above | Human-readable explanation |
| `X-Velocity-Score` | Always on create | Spend velocity score 0–100 |

---

## 7. Local Setup

### Option A — Docker (recommended)

**Prerequisites:** Docker Desktop running.

```bash
cd "path/to/Zorvyn_Backend_Assessment_Anagha_Badhe"
docker-compose up --build
```

First build takes ~3 minutes (downloads Maven + dependencies). Subsequent builds take ~15–20 seconds because Maven dependencies are cached as a Docker layer and only re-downloaded when `pom.xml` changes.

After startup, fix the admin password once (only needed on a fresh DB volume):
```bash
docker exec -it zorvyn_backend_assessment_anagha_badhe-postgres-1 psql -U postgres -d financedb -c "UPDATE users SET password_hash = '\$2a\$10\$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO' WHERE email = 'admin@finance.com';"
```

API: `http://localhost:8080` | Swagger: `http://localhost:8080/swagger-ui.html`

### Option B — Local (PostgreSQL + Redis already running)

Prerequisites: PostgreSQL 16 on port 5432 (`financedb` / `postgres` / `postgres`), Redis on port 6379.

```bash
# Start only infrastructure
docker-compose up postgres redis

# Run the app
mvn spring-boot:run
```

### Default credentials (seeded by Flyway)
| Email | Password | Role |
|---|---|---|
| `admin@finance.com` | `admin123` | ADMIN |

### Frontend
```bash
cd frontend && npm install && npm run dev
```
Frontend: `http://localhost:3000`

### Clean slate (wipe DB and re-seed)
```bash
docker-compose down -v
docker-compose up --build
```
Then re-run the admin password fix command above.

---

## 8. Running Tests

```bash
mvn test
```

### Test coverage
| Test class | Type | What it covers |
|---|---|---|
| `AuthServiceTest` | Unit (Mockito) | Register, login, duplicate email rejection |
| `TransactionServiceTest` | Unit (Mockito) | Create, idempotency, soft delete, restore, user-not-found |
| `TransactionControllerTest` | Integration (MockMvc) | HTTP layer, role enforcement (VIEWER blocked from POST) |
| `TransactionRepositoryTest` | Integration (Testcontainers) | Real PostgreSQL — save, soft delete visibility, sum queries, idempotency key lookup |

The Testcontainers test spins up a real `postgres:16-alpine` container during the test run, ensuring repository queries are validated against the actual database engine rather than H2.

---

## 9. Known Limitations & Future Improvements

- **Rate limit state is in-memory**: In a horizontally scaled deployment, buckets should be stored in Redis using Bucket4j's `RedisProxyManager`.
- **Refresh token hash is weak**: `hashCode()` is used for simplicity. Production should use `SHA-256` with a pepper.
- **No email verification**: Registered users are immediately active.
- **Audit log userId is null**: The `AuditAspect` captures the email from `SecurityContext` but resolves the userId lazily to avoid a circular dependency.
- **Merchant dictionary is static**: A production system would use a merchant classification API or ML model.
- **Category management**: Categories are seeded via Flyway. A `POST /categories` endpoint for admins would be a natural next addition.
- **Testcontainers in CI**: Requires Docker available in the CI environment. A GitHub Actions workflow with `services: postgres` would be the production CI approach.

---

## 10. Project Structure

```
src/main/java/com/financeapi/
├── config/       ← SecurityConfig, RedisConfig, OpenApiConfig, RateLimitFilter, DataInitializer
├── domain/       ← JPA entities: User, Role, Transaction, Category, RefreshToken, AuditLog,
│                   Budget, TransactionDna, MerchantTag
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
```
