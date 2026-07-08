-- Seed the baseline permission catalog and the SYSTEM_ADMIN role.
-- Idempotent so re-running against an initialised DB is safe.

INSERT INTO permissions (code, description) VALUES
    ('USER_READ',    'Read user information'),
    ('USER_WRITE',   'Create and modify users'),
    ('USER_DELETE',  'Delete users'),
    ('ROLE_READ',    'Read roles and permissions'),
    ('ROLE_WRITE',   'Manage roles and their permission assignments'),
    ('SYSTEM_ADMIN', 'Full system administration')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (name, description) VALUES
    ('SYSTEM_ADMIN', 'System administrator with full access')
ON CONFLICT (name) DO NOTHING;

-- SYSTEM_ADMIN gets every seeded permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'SYSTEM_ADMIN'
ON CONFLICT DO NOTHING;
