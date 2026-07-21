-- Supplementary Payroll + Full & Final Settlement
--
-- Extends payroll_runs with a run_type so supplementary and final-settlement runs can share the
-- same lifecycle while remaining distinguishable in reports. Adds the final_settlements table
-- for terminated-employee F&F: unused-leave encashment, gratuity, notice-pay recovery /
-- receivable, plus a link to the FINAL_SETTLEMENT run that emitted the payslip.

-- =====================================================================
-- payroll_runs: run_type
-- =====================================================================
ALTER TABLE payroll_runs
    ADD COLUMN run_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR';

ALTER TABLE payroll_runs
    ADD CONSTRAINT ck_payroll_runs_run_type
    CHECK (run_type IN ('REGULAR','SUPPLEMENTARY','FINAL_SETTLEMENT'));

CREATE INDEX ix_payroll_runs_type
    ON payroll_runs (tenant_id, company_id, run_type)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- final_settlements
-- =====================================================================
CREATE TABLE final_settlements (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    termination_date         DATE        NOT NULL,
    last_working_date        DATE        NOT NULL,
    unused_leave_days        NUMERIC(6,2) NOT NULL DEFAULT 0,
    encashment_amount        NUMERIC(18,4) NOT NULL DEFAULT 0,
    gratuity_amount          NUMERIC(18,4) NOT NULL DEFAULT 0,
    notice_pay_recovery      NUMERIC(18,4) NOT NULL DEFAULT 0,
    notice_pay_receivable    NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_earnings           NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_deductions         NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    approved_at              TIMESTAMPTZ,
    approved_by              UUID,
    settled_at               TIMESTAMPTZ,
    settled_by               UUID,
    settlement_run_id        UUID        REFERENCES payroll_runs (id),
    notes                    VARCHAR(2048),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_ffs_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_ffs_status
        CHECK (status IN ('DRAFT','APPROVED','SETTLED','CANCELLED')),
    CONSTRAINT ck_ffs_dates
        CHECK (last_working_date >= termination_date OR termination_date >= last_working_date),
    CONSTRAINT ck_ffs_amounts_nonneg
        CHECK (unused_leave_days >= 0 AND encashment_amount >= 0
               AND gratuity_amount >= 0 AND notice_pay_recovery >= 0
               AND notice_pay_receivable >= 0 AND other_earnings >= 0
               AND other_deductions >= 0),
    CONSTRAINT ck_ffs_approved_pair
        CHECK ((status <> 'APPROVED' AND status <> 'SETTLED')
               OR (approved_at IS NOT NULL AND approved_by IS NOT NULL)),
    CONSTRAINT ck_ffs_settled_pair
        CHECK ((status <> 'SETTLED')
               OR (settled_at IS NOT NULL AND settled_by IS NOT NULL AND settlement_run_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_ffs_employee_alive
    ON final_settlements (employee_id)
    WHERE deleted_at IS NULL AND status <> 'CANCELLED';

CREATE INDEX ix_ffs_company_status
    ON final_settlements (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;
