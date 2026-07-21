-- Payroll Processing + Validation + Freeze
--
-- Extends the payroll engine with:
--   * loss-of-pay tracking on payslips  (WP-008 leave integration)
--   * arrears (retro salary adjustments applied to a run)
--   * validation report + freeze on payroll runs
--
-- Backward compatible: every new column defaults so existing WP-009 payslips / runs remain valid.

-- =====================================================================
-- payslips: add LOP fields
-- =====================================================================
ALTER TABLE payslips
    ADD COLUMN lop_days           NUMERIC(6,2) NOT NULL DEFAULT 0,
    ADD COLUMN basic_effective    NUMERIC(18,4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_payslips_lop_nonneg CHECK (lop_days >= 0),
    ADD CONSTRAINT ck_payslips_basic_eff_nonneg CHECK (basic_effective >= 0);

-- =====================================================================
-- payroll_runs: freeze + validation
-- =====================================================================
ALTER TABLE payroll_runs
    ADD COLUMN frozen_at          TIMESTAMPTZ,
    ADD COLUMN frozen_by          UUID,
    ADD COLUMN validation_report  JSONB;

ALTER TABLE payroll_runs
    DROP CONSTRAINT ck_payroll_runs_status;

ALTER TABLE payroll_runs
    ADD CONSTRAINT ck_payroll_runs_status
    CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FINALIZED','FROZEN','FAILED'));

ALTER TABLE payroll_runs
    ADD CONSTRAINT ck_payroll_runs_frozen_pair
    CHECK ((status <> 'FROZEN') OR (frozen_at IS NOT NULL AND frozen_by IS NOT NULL));

-- =====================================================================
-- payroll_arrears — retro adjustments applied to a run for one employee.
-- =====================================================================
CREATE TABLE payroll_arrears (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    payroll_run_id           UUID        REFERENCES payroll_runs (id),
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    reason_code              VARCHAR(64) NOT NULL,
    description              VARCHAR(1024),
    amount                   NUMERIC(18,4) NOT NULL,
    kind                     VARCHAR(32) NOT NULL,
    for_period_start         DATE,
    for_period_end           DATE,
    applied                  BOOLEAN     NOT NULL DEFAULT FALSE,
    applied_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_arrears_kind
        CHECK (kind IN ('EARNING','DEDUCTION')),
    CONSTRAINT ck_payroll_arrears_amount
        CHECK (amount >= 0),
    CONSTRAINT ck_payroll_arrears_period
        CHECK (for_period_end IS NULL OR for_period_start IS NULL OR for_period_end >= for_period_start),
    CONSTRAINT ck_payroll_arrears_applied_pair
        CHECK ((NOT applied) OR (applied_at IS NOT NULL AND payroll_run_id IS NOT NULL))
);

CREATE INDEX ix_payroll_arrears_employee
    ON payroll_arrears (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_payroll_arrears_run
    ON payroll_arrears (tenant_id, payroll_run_id)
    WHERE deleted_at IS NULL AND payroll_run_id IS NOT NULL;

CREATE INDEX ix_payroll_arrears_pending
    ON payroll_arrears (tenant_id, employee_id, applied)
    WHERE deleted_at IS NULL AND applied = FALSE;
