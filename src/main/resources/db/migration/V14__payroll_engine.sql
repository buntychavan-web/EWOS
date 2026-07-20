-- WP-009 Payroll Engine
--
-- Per-tenant / per-company payroll: pay periods (windows), pay components (metadata catalogue),
-- employee compensation (salary structure), payroll runs (compute cycle over a period), and
-- payslips (immutable per-employee snapshot with line items).
--
-- Consumes:
--   * Employee module (WP-005)   — every payslip is per-employee.
--   * Leave module (WP-008)      — approved unpaid leave is reflected as a deduction line.
--   * Attendance module (WP-007) — future runs can reconcile hourly earners against timesheets.
--
-- Aggregates
--   payroll_periods              Monthly/semi-monthly/bi-weekly/weekly window (OPEN / LOCKED / CLOSED).
--   pay_components               Metadata catalogue (EARNING / DEDUCTION, calculation type).
--   employee_compensations       Effective-dated salary structure per employee.
--   employee_compensation_lines  Component-level overrides on a compensation record.
--   payroll_runs                 Compute cycle per (period, company). Lifecycle: PENDING → PROCESSING
--                                → COMPLETED → FINALIZED (or FAILED).
--   payslips                     Immutable per-employee snapshot for a run.
--   payslip_lines                Line items (component + kind + amount) per payslip.

-- =====================================================================
-- pay_components
-- =====================================================================
CREATE TABLE pay_components (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    description              VARCHAR(512),
    kind                     VARCHAR(32) NOT NULL,
    calculation_type         VARCHAR(32) NOT NULL,
    default_amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    default_percentage       NUMERIC(7,4)  NOT NULL DEFAULT 0,
    taxable                  BOOLEAN     NOT NULL DEFAULT TRUE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order               INTEGER     NOT NULL DEFAULT 100,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pay_components_kind
        CHECK (kind IN ('EARNING','DEDUCTION')),
    CONSTRAINT ck_pay_components_calc_type
        CHECK (calculation_type IN ('FIXED','PERCENT_OF_BASIC')),
    CONSTRAINT ck_pay_components_amount_nonneg
        CHECK (default_amount >= 0),
    CONSTRAINT ck_pay_components_pct_range
        CHECK (default_percentage >= 0 AND default_percentage <= 100)
);

CREATE UNIQUE INDEX ux_pay_components_tenant_code_alive
    ON pay_components (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_pay_components_tenant_kind_active
    ON pay_components (tenant_id, kind, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- payroll_periods
-- =====================================================================
CREATE TABLE payroll_periods (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    frequency                VARCHAR(32) NOT NULL,
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    pay_date                 DATE        NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    locked_at                TIMESTAMPTZ,
    locked_by                UUID,
    closed_at                TIMESTAMPTZ,
    closed_by                UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_periods_frequency
        CHECK (frequency IN ('MONTHLY','SEMI_MONTHLY','BI_WEEKLY','WEEKLY')),
    CONSTRAINT ck_payroll_periods_status
        CHECK (status IN ('OPEN','LOCKED','CLOSED')),
    CONSTRAINT ck_payroll_periods_date_range
        CHECK (period_end >= period_start),
    CONSTRAINT ck_payroll_periods_locked_pair
        CHECK ((status = 'OPEN') OR (locked_at IS NOT NULL AND locked_by IS NOT NULL)),
    CONSTRAINT ck_payroll_periods_closed_pair
        CHECK ((status <> 'CLOSED') OR (closed_at IS NOT NULL AND closed_by IS NOT NULL))
);

CREATE UNIQUE INDEX ux_payroll_periods_company_code_alive
    ON payroll_periods (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payroll_periods_company_range
    ON payroll_periods (tenant_id, company_id, period_start DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payroll_periods_status
    ON payroll_periods (tenant_id, status)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- employee_compensations
-- =====================================================================
CREATE TABLE employee_compensations (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    frequency                VARCHAR(32) NOT NULL,
    basic_salary             NUMERIC(18,4) NOT NULL,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    notes                    VARCHAR(1024),
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_comp_frequency
        CHECK (frequency IN ('MONTHLY','SEMI_MONTHLY','BI_WEEKLY','WEEKLY')),
    CONSTRAINT ck_employee_comp_basic_positive
        CHECK (basic_salary >= 0),
    CONSTRAINT ck_employee_comp_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_employee_comp_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_employee_comp_employee_effective
    ON employee_compensations (tenant_id, employee_id, effective_from DESC)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_employee_comp_active
    ON employee_compensations (employee_id)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- employee_compensation_lines
-- =====================================================================
CREATE TABLE employee_compensation_lines (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    compensation_id          UUID        NOT NULL REFERENCES employee_compensations (id) ON DELETE CASCADE,
    pay_component_id         UUID        NOT NULL REFERENCES pay_components (id),
    amount                   NUMERIC(18,4) NOT NULL DEFAULT 0,
    percentage               NUMERIC(7,4)  NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_emp_comp_lines_amount_nonneg
        CHECK (amount >= 0),
    CONSTRAINT ck_emp_comp_lines_pct_range
        CHECK (percentage >= 0 AND percentage <= 100)
);

CREATE UNIQUE INDEX ux_emp_comp_lines_comp_component
    ON employee_compensation_lines (compensation_id, pay_component_id);

CREATE INDEX ix_emp_comp_lines_tenant
    ON employee_compensation_lines (tenant_id);

-- =====================================================================
-- payroll_runs
-- =====================================================================
CREATE TABLE payroll_runs (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    payroll_period_id        UUID        NOT NULL REFERENCES payroll_periods (id),
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at               TIMESTAMPTZ,
    started_by               UUID,
    completed_at             TIMESTAMPTZ,
    finalized_at             TIMESTAMPTZ,
    finalized_by             UUID,
    failed_at                TIMESTAMPTZ,
    failure_reason           VARCHAR(2048),
    employees_processed      INTEGER     NOT NULL DEFAULT 0,
    total_gross              NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_deductions         NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_net                NUMERIC(18,4) NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_runs_status
        CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FINALIZED','FAILED')),
    CONSTRAINT ck_payroll_runs_totals_nonneg
        CHECK (total_gross >= 0 AND total_deductions >= 0 AND total_net >= 0),
    CONSTRAINT ck_payroll_runs_finalized_pair
        CHECK ((status <> 'FINALIZED') OR (finalized_at IS NOT NULL AND finalized_by IS NOT NULL))
);

CREATE INDEX ix_payroll_runs_period
    ON payroll_runs (tenant_id, payroll_period_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payroll_runs_company_status
    ON payroll_runs (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- payslips
-- =====================================================================
CREATE TABLE payslips (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    payroll_run_id           UUID        NOT NULL REFERENCES payroll_runs (id),
    payroll_period_id        UUID        NOT NULL REFERENCES payroll_periods (id),
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    employee_number_snapshot VARCHAR(64) NOT NULL,
    employee_name_snapshot   VARCHAR(256) NOT NULL,
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    pay_date                 DATE        NOT NULL,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    gross_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    deductions_amount        NUMERIC(18,4) NOT NULL DEFAULT 0,
    net_amount               NUMERIC(18,4) NOT NULL DEFAULT 0,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    finalized_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payslips_status
        CHECK (status IN ('DRAFT','FINALIZED')),
    CONSTRAINT ck_payslips_totals_nonneg
        CHECK (gross_amount >= 0 AND deductions_amount >= 0 AND net_amount >= 0),
    CONSTRAINT ck_payslips_currency_len
        CHECK (LENGTH(currency) = 3)
);

CREATE UNIQUE INDEX ux_payslips_run_employee
    ON payslips (payroll_run_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payslips_employee_period
    ON payslips (tenant_id, employee_id, period_start DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payslips_company_period
    ON payslips (tenant_id, company_id, payroll_period_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- payslip_lines
-- =====================================================================
CREATE TABLE payslip_lines (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    payslip_id               UUID        NOT NULL REFERENCES payslips (id) ON DELETE CASCADE,
    pay_component_id         UUID        NOT NULL REFERENCES pay_components (id),
    component_code_snapshot  VARCHAR(64) NOT NULL,
    component_name_snapshot  VARCHAR(128) NOT NULL,
    kind                     VARCHAR(32) NOT NULL,
    calculation_type         VARCHAR(32) NOT NULL,
    amount                   NUMERIC(18,4) NOT NULL DEFAULT 0,
    percentage_applied       NUMERIC(7,4)  NOT NULL DEFAULT 0,
    sort_order               INTEGER     NOT NULL DEFAULT 100,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payslip_lines_kind
        CHECK (kind IN ('EARNING','DEDUCTION')),
    CONSTRAINT ck_payslip_lines_calc_type
        CHECK (calculation_type IN ('FIXED','PERCENT_OF_BASIC')),
    CONSTRAINT ck_payslip_lines_amount_nonneg
        CHECK (amount >= 0)
);

CREATE INDEX ix_payslip_lines_payslip
    ON payslip_lines (payslip_id);

CREATE INDEX ix_payslip_lines_component
    ON payslip_lines (tenant_id, pay_component_id);

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PAYROLL_READ',    'Read payroll periods, components, compensation, runs, and payslips'),
    (gen_random_uuid(), 'PAYROLL_WRITE',   'Create and edit payroll periods, components, and compensation'),
    (gen_random_uuid(), 'PAYROLL_RUN',     'Start and finalize payroll runs'),
    (gen_random_uuid(), 'PAYROLL_ADMIN',   'Full payroll administration including component catalogue');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('PAYROLL_READ','PAYROLL_WRITE','PAYROLL_RUN','PAYROLL_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
