-- Sprint 8.1.1: Dashboard summary permission.
--
-- Grants the aggregate read that powers GET /api/dashboard/summary. Kept
-- separate from USER_READ / ROLE_READ so an operator can be scoped to
-- summary counts without seeing the underlying rows.

INSERT INTO permissions (code, description) VALUES
    ('DASHBOARD_READ', 'Read aggregate dashboard counters')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SYSTEM_ADMIN' AND p.code = 'DASHBOARD_READ'
ON CONFLICT DO NOTHING;
