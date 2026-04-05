-- Seed analyst user (password: analyst123)
INSERT INTO users (email, password_hash, full_name)
VALUES ('analyst@finance.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Analyst')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'analyst@finance.com' AND r.name = 'ANALYST'
ON CONFLICT DO NOTHING;

-- Seed viewer user (password: viewer123)
INSERT INTO users (email, password_hash, full_name)
VALUES ('viewer@finance.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'System Viewer')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
    SELECT u.id, r.id FROM users u, roles r
    WHERE u.email = 'viewer@finance.com' AND r.name = 'VIEWER'
ON CONFLICT DO NOTHING;
