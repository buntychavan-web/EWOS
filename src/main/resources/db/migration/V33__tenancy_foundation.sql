-- Sprint 14.1 — Outsourced Payroll Foundation: Tenant / Client / Company hierarchy,
-- Service catalogue, Payroll Service Provider, Client Assignment (Chinese Wall).
--
-- Design notes (see SPRINT_14_TECHNICAL_DESIGN_v3.md for the full rationale)
--   * Tenant -> Client -> Company is a strictly additive hierarchy. No existing table's schema
--     changes: employees/organization_units/payroll_*/etc. keep their existing bare tenant_id /
--     company_id columns exactly as-is. This migration only adds new lookup tables for those
--     columns to eventually reference (FK-hardening is intentionally deferred, per the design doc).
--   * client_assignments.user_id is a soft reference to identity.users (UUID, no FK) — the same
--     "no cross-module hard FK" convention already used for created_by/updated_by everywhere and
--     documented explicitly for employees.person_id ("soft reference — no FK yet").
--   * services is the generic Service Catalogue dictionary (per-tenant, zero hardcoded vocabulary
--     — same shape as organization_unit_types). client_assignments.service_id is optional: NULL
--     means "full client access" (today's default), a specific value narrows to one service.
--   * Every table follows the established soft-delete + @Version + audit pattern
--     (deleted_at / version_no / created_at / updated_at / created_by / updated_by).

-- =====================================================================
-- tenants — platform isolation / licensing boundary
-- =====================================================================
CREATE TABLE tenants (
    id              UUID        PRIMARY KEY,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE UNIQUE INDEX ux_tenants_code_alive
    ON tenants (LOWER(code))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- clients — commercial customer relationship within a tenant
-- =====================================================================
CREATE TABLE clients (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    code            VARCHAR(64) NOT NULL,
    legal_name      VARCHAR(256) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    onboarded_at    DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_clients_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE UNIQUE INDEX ux_clients_tenant_code_alive
    ON clients (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_clients_tenant ON clients (tenant_id) WHERE deleted_at IS NULL;

-- =====================================================================
-- companies — legal entity under a client (materializes the company_id
-- bare UUID already referenced by employees/organization_units/payroll_*/etc.)
-- =====================================================================
CREATE TABLE companies (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    client_id       UUID        NOT NULL REFERENCES clients (id),
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    country_code    VARCHAR(2),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_companies_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    CONSTRAINT ck_companies_country_code CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$')
);

CREATE UNIQUE INDEX ux_companies_client_code_alive
    ON companies (client_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_companies_client ON companies (client_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_companies_tenant ON companies (tenant_id) WHERE deleted_at IS NULL;

-- =====================================================================
-- services — Service Catalogue metadata dictionary (per-tenant, zero
-- hardcoded vocabulary — same shape as organization_unit_types)
-- =====================================================================
CREATE TABLE services (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    category        VARCHAR(64),
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_services_tenant_code_alive
    ON services (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_services_tenant_active ON services (tenant_id, active) WHERE deleted_at IS NULL;

-- =====================================================================
-- payroll_service_providers — the vendor organization operating the
-- platform for its clients
-- =====================================================================
CREATE TABLE payroll_service_providers (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(256) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_providers_status CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED'))
);

CREATE UNIQUE INDEX ux_providers_tenant_code_alive
    ON payroll_service_providers (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- client_assignments — which provider user may work on which client
-- (optionally narrowed to one service). THE Chinese Wall enforcement table.
-- =====================================================================
CREATE TABLE client_assignments (
    id                  UUID        PRIMARY KEY,
    provider_id         UUID        NOT NULL REFERENCES payroll_service_providers (id),
    user_id             UUID        NOT NULL,
    client_id           UUID        NOT NULL REFERENCES clients (id),
    service_id          UUID        REFERENCES services (id),
    scope_role          VARCHAR(32) NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_assignments_scope_role
        CHECK (scope_role IN ('PAYROLL_PROCESSOR','PAYROLL_REVIEWER','PAYROLL_ADMIN','AUDITOR_READ_ONLY')),
    CONSTRAINT ck_client_assignments_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_client_assignments_user_client
    ON client_assignments (user_id, client_id)
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE INDEX ix_client_assignments_client ON client_assignments (client_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_client_assignments_provider ON client_assignments (provider_id) WHERE deleted_at IS NULL;

-- =====================================================================
-- Identity permissions for the Tenancy module
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'TENANT_READ',  'Read tenants'),
    (gen_random_uuid(), 'TENANT_WRITE', 'Create and update tenants'),
    (gen_random_uuid(), 'TENANT_ADMIN', 'Close and reactivate tenants'),
    (gen_random_uuid(), 'CLIENT_READ',  'Read clients'),
    (gen_random_uuid(), 'CLIENT_WRITE', 'Create and update clients'),
    (gen_random_uuid(), 'CLIENT_ADMIN', 'Close clients; bypass per-client Chinese Wall scoping'),
    (gen_random_uuid(), 'COMPANY_READ',  'Read companies'),
    (gen_random_uuid(), 'COMPANY_WRITE', 'Create and update companies'),
    (gen_random_uuid(), 'COMPANY_ADMIN', 'Close companies'),
    (gen_random_uuid(), 'SERVICE_READ',  'Read the service catalogue'),
    (gen_random_uuid(), 'SERVICE_WRITE', 'Create and update service catalogue entries'),
    (gen_random_uuid(), 'SERVICE_ADMIN', 'Deactivate service catalogue entries'),
    (gen_random_uuid(), 'PROVIDER_READ',  'Read payroll service providers'),
    (gen_random_uuid(), 'PROVIDER_WRITE', 'Create and update payroll service providers'),
    (gen_random_uuid(), 'PROVIDER_ADMIN', 'Close payroll service providers'),
    (gen_random_uuid(), 'CLIENT_ASSIGNMENT_READ',  'Read client assignments'),
    (gen_random_uuid(), 'CLIENT_ASSIGNMENT_WRITE', 'Create, update and revoke client assignments');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'TENANT_READ','TENANT_WRITE','TENANT_ADMIN',
    'CLIENT_READ','CLIENT_WRITE','CLIENT_ADMIN',
    'COMPANY_READ','COMPANY_WRITE','COMPANY_ADMIN',
    'SERVICE_READ','SERVICE_WRITE','SERVICE_ADMIN',
    'PROVIDER_READ','PROVIDER_WRITE','PROVIDER_ADMIN',
    'CLIENT_ASSIGNMENT_READ','CLIENT_ASSIGNMENT_WRITE'
)
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
