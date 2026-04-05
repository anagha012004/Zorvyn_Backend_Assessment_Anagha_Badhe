-- Velocity score + timezone on users
ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC';
ALTER TABLE users ADD COLUMN IF NOT EXISTS velocity_score DOUBLE PRECISION DEFAULT 0.0;

-- Transaction DNA fingerprints
CREATE TABLE IF NOT EXISTS transaction_dna (
    id BIGSERIAL PRIMARY KEY,
    dna_hash VARCHAR(64) NOT NULL,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_dna_hash_created ON transaction_dna(dna_hash, created_at);

-- Budget envelopes
CREATE TABLE IF NOT EXISTS budgets (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id),
    month_year VARCHAR(7) NOT NULL,   -- e.g. '2025-06'
    amount_limit NUMERIC(19,2) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    UNIQUE (category_id, month_year)
);

-- Merchant tags
CREATE TABLE IF NOT EXISTS merchant_tags (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE REFERENCES transactions(id),
    merchant_name VARCHAR(255) NOT NULL
);
