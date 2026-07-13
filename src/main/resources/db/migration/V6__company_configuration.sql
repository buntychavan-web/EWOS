-- Sprint 6: Company Configuration.
--
-- Introduces the multi-company model with:
--   * tenants — top-level owner of one or more companies, with configurable
--     data-isolation policy (SHARED / SEGREGATED).
--   * companies — master record per company, one row per company for the JOIN
--     target of everything else. Effective-dated profile snapshots live in
--     company_versions.
--   * company_versions — immutable, effective-dated profile snapshots. A
--     "profile edit" produces a new version and closes the previous one.
--   * statutory_registrations — multiple per company, effective-dated,
--     PAN nationally unique.
--   * company_bank_accounts — multiple per company, by purpose, effective-dated.
--   * company_policy_assignments — references to policies (Holiday Calendar,
--     Payroll Calendar, Leave / Attendance / Shift, Workflow). Policy tables
--     themselves live in future sprints; this holds only the reference + date.
--   * company_shared_services — team assignments (HR / Payroll / Finance / IT),
--     effective-dated.
--
-- Effective-date overlap prevention is enforced at the service layer for
-- portability (avoids requiring the btree_gist extension in every environment).

-- ---------- tenants ----------
CREATE TABLE tenants (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code              VARCHAR(50)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    isolation_policy  VARCHAR(20)  NOT NULL DEFAULT 'SEGREGATED'
                      CHECK (isolation_policy IN ('SHARED', 'SEGREGATED')),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);
CREATE UNIQUE INDEX ux_tenants_code_active ON tenants(code) WHERE deleted_at IS NULL;

-- A default tenant is seeded so every existing (and Sprint-6-created) row has a
-- non-null owner. Product can rename it later.
INSERT INTO tenants (code, name, isolation_policy)
VALUES ('DEFAULT', 'Default Tenant', 'SEGREGATED');

-- ---------- companies ----------
CREATE TABLE companies (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID        NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    code        VARCHAR(50) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID
);
CREATE INDEX idx_companies_tenant ON companies(tenant_id);
CREATE UNIQUE INDEX ux_companies_tenant_code_active
    ON companies(tenant_id, code) WHERE deleted_at IS NULL;

-- ---------- company_versions ----------
-- One row per company profile snapshot. Immutable except for effective_to.
CREATE TABLE company_versions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id                UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    effective_from            DATE         NOT NULL,
    effective_to              DATE,                             -- null = current, open-ended
    name                      VARCHAR(255) NOT NULL,
    legal_name                VARCHAR(255) NOT NULL,
    logo_url                  VARCHAR(500),
    timezone                  VARCHAR(64)  NOT NULL,
    currency                  CHAR(3)      NOT NULL,
    fiscal_year_start_month   SMALLINT     NOT NULL CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_by                UUID,
    CONSTRAINT ck_company_versions_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_company_versions_company_from ON company_versions(company_id, effective_from DESC);
-- At most one open-ended (current) version per company.
CREATE UNIQUE INDEX ux_company_versions_open ON company_versions(company_id) WHERE effective_to IS NULL;

-- ---------- statutory_registrations ----------
CREATE TABLE statutory_registrations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id            UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    kind                  VARCHAR(30)  NOT NULL
                          CHECK (kind IN ('PAN','TAN','GST','PF','ESIC','PT','LWF')),
    registration_number   VARCHAR(50)  NOT NULL,
    jurisdiction          VARCHAR(100),                         -- e.g. state for GST/PT/LWF
    effective_from        DATE         NOT NULL,
    effective_to          DATE,
    deleted_at            TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    CONSTRAINT ck_statutory_registrations_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_statutory_registrations_company_kind
    ON statutory_registrations(company_id, kind, effective_from DESC);
-- PAN is nationally unique in India; enforce across live rows regardless of company.
CREATE UNIQUE INDEX ux_statutory_registrations_pan_active
    ON statutory_registrations(registration_number)
    WHERE kind = 'PAN' AND deleted_at IS NULL;

-- ---------- company_bank_accounts ----------
CREATE TABLE company_bank_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    purpose          VARCHAR(30)  NOT NULL
                     CHECK (purpose IN ('SALARY','FULL_AND_FINAL','REIMBURSEMENT','STATUTORY','VENDOR','OTHER')),
    account_name     VARCHAR(255) NOT NULL,
    account_number   VARCHAR(50)  NOT NULL,
    bank_name        VARCHAR(255) NOT NULL,
    branch           VARCHAR(255),
    ifsc_or_swift    VARCHAR(30),
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    effective_from   DATE         NOT NULL,
    effective_to     DATE,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    CONSTRAINT ck_company_bank_accounts_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_company_bank_accounts_company_purpose
    ON company_bank_accounts(company_id, purpose);

-- ---------- company_policy_assignments ----------
-- References-only: the actual policy tables (holiday calendars, leave policies,
-- shift patterns, workflows) arrive in future sprints. This table holds the
-- (company, policy_type, policy_ref) tuple and its effective window.
CREATE TABLE company_policy_assignments (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    policy_type       VARCHAR(40)  NOT NULL
                      CHECK (policy_type IN
                          ('HOLIDAY_CALENDAR','PAYROLL_CALENDAR','LEAVE_POLICY',
                           'ATTENDANCE_POLICY','SHIFT_POLICY','WORKFLOW')),
    policy_ref        UUID         NOT NULL,
    policy_label      VARCHAR(255),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_company_policy_assignments_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_company_policy_assignments_company_type
    ON company_policy_assignments(company_id, policy_type, effective_from DESC);

-- ---------- company_shared_services ----------
CREATE TABLE company_shared_services (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        UUID         NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    team_type         VARCHAR(30)  NOT NULL
                      CHECK (team_type IN ('HR','PAYROLL','FINANCE','IT')),
    team_ref          UUID         NOT NULL,
    team_label        VARCHAR(255),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_company_shared_services_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_company_shared_services_company_type
    ON company_shared_services(company_id, team_type, effective_from DESC);

-- ---------- Permissions ----------
INSERT INTO permissions (code, description) VALUES
    ('COMPANY_READ',   'Read company configuration'),
    ('COMPANY_WRITE',  'Create and modify company configuration'),
    ('COMPANY_DELETE', 'Soft-delete companies')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.code IN ('COMPANY_READ','COMPANY_WRITE','COMPANY_DELETE')
ON CONFLICT DO NOTHING;
