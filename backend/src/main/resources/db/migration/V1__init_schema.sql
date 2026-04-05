CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    color_hex VARCHAR(7),
    icon VARCHAR(50)
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(19,2) NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('INCOME','EXPENSE')),
    category_id BIGINT REFERENCES categories(id),
    date DATE NOT NULL,
    notes TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed roles
INSERT INTO roles (name) VALUES ('VIEWER'), ('ANALYST'), ('ADMIN');

-- Seed default admin (password: admin123)
INSERT INTO users (email, password_hash, full_name) VALUES
    ('admin@finance.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Admin');

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r WHERE u.email = 'admin@finance.com' AND r.name = 'ADMIN';

-- Seed categories
INSERT INTO categories (name, color_hex, icon) VALUES
    ('Food', '#FF6B6B', 'utensils'),
    ('Transport', '#4ECDC4', 'car'),
    ('Salary', '#45B7D1', 'briefcase'),
    ('Entertainment', '#96CEB4', 'film'),
    ('Utilities', '#FFEAA7', 'zap'),
    ('Healthcare', '#DDA0DD', 'heart'),
    ('Other', '#B0B0B0', 'more-horizontal');
