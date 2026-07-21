-- Payroll Accounting & General Ledger (M6)
--
-- Adds the GL config and journal generation surface for payroll runs. Metadata-driven so tenants
-- can plug in any chart-of-accounts. Cost centres, business units, and per-employee cost
-- allocation feed the dimension columns on each journal line so downstream reporting can slice
-- by cost centre / business unit / department without re-joining the payroll payload.
--
-- Ships with a generic CSV export at the application layer; SAP IDoc, Oracle EBS staging, and
-- Microsoft Dynamics D365 writers are pluggable — the export table just records the choice and
-- reference so those writers can be added later without another schema change.

-- =====================================================================
-- Dimensions: cost centres + business units
-- =====================================================================
CREATE TABLE cost_centres (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    company_id   UUID        NOT NULL,
    code         VARCHAR(64) NOT NULL,
    name         VARCHAR(256) NOT NULL,
    description  VARCHAR(1024),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    version_no   BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_cost_centres_company_code_alive
    ON cost_centres (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE business_units (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    company_id   UUID        NOT NULL,
    code         VARCHAR(64) NOT NULL,
    name         VARCHAR(256) NOT NULL,
    description  VARCHAR(1024),
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    version_no   BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_business_units_company_code_alive
    ON business_units (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Per-employee cost allocation
-- =====================================================================
CREATE TABLE employee_cost_allocations (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID        NOT NULL,
    employee_id         UUID        NOT NULL REFERENCES employees (id),
    cost_centre_id      UUID        REFERENCES cost_centres (id),
    business_unit_id    UUID        REFERENCES business_units (id),
    department_org_unit_id UUID     REFERENCES organization_units (id),
    percentage          NUMERIC(7,4) NOT NULL DEFAULT 100.0000,
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_eca_percentage
        CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT ck_eca_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_eca_employee_active
    ON employee_cost_allocations (tenant_id, employee_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- GL chart of accounts + mappings
-- =====================================================================
CREATE TABLE gl_accounts (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    company_id   UUID        NOT NULL,
    code         VARCHAR(64) NOT NULL,
    name         VARCHAR(256) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    version_no   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_gl_accounts_type
        CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE','SUSPENSE'))
);

CREATE UNIQUE INDEX ux_gl_accounts_company_code_alive
    ON gl_accounts (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE gl_mappings (
    id                     UUID        PRIMARY KEY,
    tenant_id              UUID        NOT NULL,
    company_id             UUID        NOT NULL,
    source_kind            VARCHAR(32) NOT NULL,
    source_code            VARCHAR(64) NOT NULL,
    debit_account_id       UUID        NOT NULL REFERENCES gl_accounts (id),
    credit_account_id      UUID        NOT NULL REFERENCES gl_accounts (id),
    allocation_dimension   VARCHAR(32) NOT NULL DEFAULT 'NONE',
    description            VARCHAR(512),
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    deleted_at             TIMESTAMPTZ,
    version_no             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_gl_mappings_source_kind
        CHECK (source_kind IN ('PAY_COMPONENT','EMPLOYER_CONTRIBUTION','NET_PAY','PROVISION','ACCRUAL','STATUTORY')),
    CONSTRAINT ck_gl_mappings_dimension
        CHECK (allocation_dimension IN ('NONE','COST_CENTRE','BUSINESS_UNIT','DEPARTMENT'))
);

CREATE UNIQUE INDEX ux_gl_mappings_key_alive
    ON gl_mappings (tenant_id, company_id, source_kind, LOWER(source_code))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Payroll journals + lines
-- =====================================================================
CREATE TABLE payroll_journals (
    id                 UUID        PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    company_id         UUID        NOT NULL,
    payroll_run_id     UUID        NOT NULL REFERENCES payroll_runs (id),
    journal_number     VARCHAR(64) NOT NULL,
    journal_date       DATE        NOT NULL,
    journal_type       VARCHAR(32) NOT NULL,
    status             VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    currency           VARCHAR(3)  NOT NULL DEFAULT 'USD',
    total_debit        NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_credit       NUMERIC(18,4) NOT NULL DEFAULT 0,
    approved_at        TIMESTAMPTZ,
    approved_by        UUID,
    posted_at          TIMESTAMPTZ,
    posted_by          UUID,
    exported_at        TIMESTAMPTZ,
    exported_by        UUID,
    export_format      VARCHAR(32),
    export_reference   VARCHAR(128),
    notes              VARCHAR(2048),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version_no         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_journals_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_journals_type
        CHECK (journal_type IN ('PAYROLL','PROVISION','ACCRUAL','REVERSAL')),
    CONSTRAINT ck_journals_status
        CHECK (status IN ('DRAFT','APPROVED','POSTED','EXPORTED','REVERSED','CANCELLED')),
    CONSTRAINT ck_journals_totals_nonneg
        CHECK (total_debit >= 0 AND total_credit >= 0),
    CONSTRAINT ck_journals_approved_pair
        CHECK ((status IN ('DRAFT','CANCELLED'))
               OR (approved_at IS NOT NULL AND approved_by IS NOT NULL)),
    CONSTRAINT ck_journals_posted_pair
        CHECK ((status <> 'POSTED' AND status <> 'EXPORTED')
               OR (posted_at IS NOT NULL AND posted_by IS NOT NULL))
);

CREATE UNIQUE INDEX ux_journals_company_number_alive
    ON payroll_journals (tenant_id, company_id, LOWER(journal_number))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_journals_run
    ON payroll_journals (tenant_id, payroll_run_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_journals_status
    ON payroll_journals (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE payroll_journal_lines (
    id                      UUID        PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    journal_id              UUID        NOT NULL REFERENCES payroll_journals (id) ON DELETE CASCADE,
    line_no                 INTEGER     NOT NULL,
    gl_account_code         VARCHAR(64) NOT NULL,
    gl_account_name_snapshot VARCHAR(256) NOT NULL,
    account_type_snapshot   VARCHAR(32) NOT NULL,
    cost_centre_code        VARCHAR(64),
    business_unit_code      VARCHAR(64),
    department_code         VARCHAR(64),
    source_kind             VARCHAR(32) NOT NULL,
    source_reference        VARCHAR(128),
    debit_amount            NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit_amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency                VARCHAR(3)  NOT NULL DEFAULT 'USD',
    description             VARCHAR(512),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    version_no              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_jl_amounts_nonneg
        CHECK (debit_amount >= 0 AND credit_amount >= 0),
    CONSTRAINT ck_jl_exclusive
        CHECK ((debit_amount = 0 AND credit_amount > 0)
               OR (debit_amount > 0 AND credit_amount = 0)),
    CONSTRAINT ck_jl_source_kind
        CHECK (source_kind IN ('PAY_COMPONENT','EMPLOYER_CONTRIBUTION','NET_PAY','PROVISION','ACCRUAL','STATUTORY','BALANCING')),
    CONSTRAINT ck_jl_account_type
        CHECK (account_type_snapshot IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE','SUSPENSE'))
);

CREATE INDEX ix_jl_journal
    ON payroll_journal_lines (tenant_id, journal_id);

CREATE INDEX ix_jl_account
    ON payroll_journal_lines (tenant_id, gl_account_code);

CREATE INDEX ix_jl_dimensions
    ON payroll_journal_lines (tenant_id, cost_centre_code, business_unit_code, department_code);

-- =====================================================================
-- Scheduled reports (M7 stub — cron parsing & dispatch is handled by
-- the reports service; this table only stores the schedule + last run.)
-- =====================================================================
CREATE TABLE scheduled_reports (
    id               UUID        PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    company_id       UUID        NOT NULL,
    report_code      VARCHAR(64) NOT NULL,
    name             VARCHAR(256) NOT NULL,
    cron_expression  VARCHAR(128) NOT NULL,
    format           VARCHAR(32) NOT NULL DEFAULT 'CSV',
    parameters_json  JSONB       NOT NULL DEFAULT '{}'::JSONB,
    recipients       VARCHAR(2048),
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    last_run_at      TIMESTAMPTZ,
    last_run_status  VARCHAR(32),
    last_run_message VARCHAR(2048),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version_no       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_scheduled_reports_format
        CHECK (format IN ('CSV','JSON','EXCEL','PDF'))
);

CREATE INDEX ix_scheduled_reports_active
    ON scheduled_reports (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PAYROLL_ACCOUNTING', 'Manage GL config, generate/approve/post payroll journals, export to ERP'),
    (gen_random_uuid(), 'PAYROLL_REPORTS',    'View, run, and schedule payroll reports');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('PAYROLL_ACCOUNTING','PAYROLL_REPORTS')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
