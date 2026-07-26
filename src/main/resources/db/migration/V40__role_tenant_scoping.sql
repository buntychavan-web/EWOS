-- Sprint 1.4: tenant-scoped custom roles.
-- NULL tenant_id = system role (SYSTEM_ADMIN today); non-null = a tenant's own custom role.
-- No hard FK to tenants(id) — cross-module reference, mirrors employees.tenant_id/company_id/user_id's
-- existing plain-UUID convention rather than an intra-module hard FK.

ALTER TABLE roles
    ADD COLUMN tenant_id UUID;

CREATE INDEX idx_roles_tenant_id ON roles (tenant_id) WHERE tenant_id IS NOT NULL;

-- Replaces V5's platform-wide ux_roles_name_active(name): a role name must be unique within its own
-- scope (once among system roles, once per tenant among that tenant's custom roles), but two different
-- tenants may each have a role named e.g. "Payroll Reviewer" without conflict.
DROP INDEX IF EXISTS ux_roles_name_active;

CREATE UNIQUE INDEX uq_roles_tenant_name
    ON roles (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'::uuid), name)
    WHERE deleted_at IS NULL;
