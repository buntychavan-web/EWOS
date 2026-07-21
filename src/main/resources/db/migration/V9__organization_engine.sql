-- WP-004 Organization Engine
--
-- Multi-tenant, multi-company, hierarchical organizational units.
--
-- Design notes
--   * Every row is tenant-scoped via NOT NULL tenant_id. There is no tenants table yet;
--     when the tenant module lands it will add the FK via a later migration.
--   * unit_type_id references a per-tenant metadata dictionary — no ENUM in the DB so
--     new unit types (region, market, guild, ...) can be added without a schema change.
--   * Soft delete via deleted_at + partial unique indexes; identical pattern to identity.
--   * effective_from / effective_to model the organisational history of a unit (rename,
--     re-parent, close). Current state = row where effective_to IS NULL.
--   * @Version optimistic locking via version_no column.

-- =====================================================================
-- organization_unit_types  (metadata dictionary)
-- =====================================================================
CREATE TABLE organization_unit_types (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_org_unit_types_tenant_code_alive
    ON organization_unit_types (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_unit_types_tenant_active
    ON organization_unit_types (tenant_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- organization_units
-- =====================================================================
CREATE TABLE organization_units (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID        NOT NULL,
    unit_type_id        UUID        NOT NULL REFERENCES organization_unit_types (id),
    parent_id           UUID        REFERENCES organization_units (id),
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(1024),
    country_code        CHAR(2),
    cost_center_code    VARCHAR(64),
    manager_person_id   UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_org_units_status         CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    CONSTRAINT ck_org_units_effective_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_org_units_no_self_parent  CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX ux_org_units_tenant_company_code_alive
    ON organization_units (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_units_tenant_company
    ON organization_units (tenant_id, company_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_units_tenant_parent
    ON organization_units (tenant_id, parent_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_units_tenant_type
    ON organization_units (tenant_id, unit_type_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_units_tenant_status
    ON organization_units (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_org_units_manager
    ON organization_units (manager_person_id)
    WHERE manager_person_id IS NOT NULL AND deleted_at IS NULL;

-- =====================================================================
-- Identity permissions for Organization module
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'ORG_READ',  'Read organization units and their types'),
    (gen_random_uuid(), 'ORG_WRITE', 'Create and update organization units and their types'),
    (gen_random_uuid(), 'ORG_ADMIN', 'Close, reopen, and re-parent organization units');

-- Grant new perms to SYSTEM_ADMIN if the role is present.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ORG_READ','ORG_WRITE','ORG_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
