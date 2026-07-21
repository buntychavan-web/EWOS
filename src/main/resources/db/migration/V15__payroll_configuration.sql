-- Payroll Configuration + Employee Payroll Assignment
--
-- Extends the WP-009 payroll engine with:
--   * pay_groups                     — per-company employee grouping used to schedule runs
--   * statutory_settings             — jurisdiction-scoped, effective-dated KV configuration
--                                       (income-tax slabs, PF percentages, ESI limits, TDS caps,
--                                       and any other jurisdiction number the calculator needs)
--   * employee_bank_accounts         — one or more bank accounts per employee, one marked primary
--   * employee_payroll_profiles      — links an employee to a pay group and stores statutory
--                                       identifiers (JSONB, per-country flexible)
--
-- Attaches the (nullable) pay_group_id column to employee_compensations so a compensation row can
-- be scoped to a group without breaking existing rows.

-- =====================================================================
-- pay_groups
-- =====================================================================
CREATE TABLE pay_groups (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    description              VARCHAR(512),
    frequency                VARCHAR(32) NOT NULL,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    pay_day_of_month         INTEGER,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pay_groups_frequency
        CHECK (frequency IN ('MONTHLY','SEMI_MONTHLY','BI_WEEKLY','WEEKLY')),
    CONSTRAINT ck_pay_groups_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_pay_groups_pay_day
        CHECK (pay_day_of_month IS NULL OR pay_day_of_month BETWEEN 1 AND 31)
);

CREATE UNIQUE INDEX ux_pay_groups_company_code_alive
    ON pay_groups (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_pay_groups_tenant_company_active
    ON pay_groups (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- statutory_settings
-- =====================================================================
CREATE TABLE statutory_settings (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    jurisdiction             VARCHAR(16) NOT NULL,
    code                     VARCHAR(128) NOT NULL,
    name                     VARCHAR(256) NOT NULL,
    description              VARCHAR(1024),
    value_numeric            NUMERIC(18,6),
    value_string             VARCHAR(1024),
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_statutory_settings_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_statutory_settings_value_present
        CHECK (value_numeric IS NOT NULL OR value_string IS NOT NULL)
);

CREATE INDEX ix_statutory_settings_lookup
    ON statutory_settings (tenant_id, jurisdiction, code, effective_from DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_statutory_settings_active_key
    ON statutory_settings (tenant_id, jurisdiction, LOWER(code), effective_from)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- employee_bank_accounts
-- =====================================================================
CREATE TABLE employee_bank_accounts (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    bank_name                VARCHAR(256) NOT NULL,
    branch                   VARCHAR(256),
    account_holder_name      VARCHAR(256) NOT NULL,
    account_number           VARCHAR(64) NOT NULL,
    account_number_masked    VARCHAR(64) NOT NULL,
    routing_code             VARCHAR(64),
    swift_bic                VARCHAR(16),
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    country_code             VARCHAR(2)  NOT NULL,
    is_primary               BOOLEAN     NOT NULL DEFAULT TRUE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_bank_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_employee_bank_country_len
        CHECK (LENGTH(country_code) = 2)
);

CREATE INDEX ix_employee_bank_employee
    ON employee_bank_accounts (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_employee_bank_primary
    ON employee_bank_accounts (employee_id)
    WHERE deleted_at IS NULL AND is_primary = TRUE AND active = TRUE;

-- =====================================================================
-- employee_payroll_profiles
-- =====================================================================
CREATE TABLE employee_payroll_profiles (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    pay_group_id             UUID        REFERENCES pay_groups (id),
    tax_regime               VARCHAR(64),
    country_code             VARCHAR(2)  NOT NULL,
    statutory_identifiers    JSONB       NOT NULL DEFAULT '{}'::JSONB,
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_epp_country_len
        CHECK (LENGTH(country_code) = 2),
    CONSTRAINT ck_epp_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE UNIQUE INDEX ux_epp_active
    ON employee_payroll_profiles (employee_id)
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE INDEX ix_epp_paygroup
    ON employee_payroll_profiles (tenant_id, pay_group_id)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- Extend employee_compensations with pay_group_id
-- =====================================================================
ALTER TABLE employee_compensations
    ADD COLUMN pay_group_id UUID REFERENCES pay_groups (id);

CREATE INDEX ix_employee_comp_paygroup
    ON employee_compensations (tenant_id, pay_group_id)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- Additional permission
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PAYROLL_CONFIG', 'Manage payroll configuration (pay groups, statutory settings, employee profiles)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'PAYROLL_CONFIG'
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
