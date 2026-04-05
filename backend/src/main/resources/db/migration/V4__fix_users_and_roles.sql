-- Fix analyst password to analyst123
UPDATE users
SET password_hash = '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO'
WHERE email = 'analyst@finance.com';

-- Fix viewer password to viewer123
UPDATE users
SET password_hash = '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO'
WHERE email = 'viewer@finance.com';

-- Fix admin password to admin123
UPDATE users
SET password_hash = '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO1GRe/0lzO'
WHERE email = 'admin@finance.com';

-- Ensure analyst also has VIEWER role (required for hasAnyRole checks)
INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'analyst@finance.com' AND r.name = 'VIEWER'
ON CONFLICT DO NOTHING;

-- Ensure admin has all three roles
INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'admin@finance.com' AND r.name IN ('VIEWER', 'ANALYST', 'ADMIN')
ON CONFLICT DO NOTHING;

-- Add timezone column default if missing
UPDATE users SET timezone = 'Asia/Kolkata' WHERE timezone IS NULL OR timezone = 'UTC';
