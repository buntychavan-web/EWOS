-- Sprint 24L item 4 — Loan & Recovery Engine.
--
-- employee_loans              — principal, interest terms, running outstanding_principal balance.
-- loan_schedule_installments  — the full amortization schedule, generated once at loan creation
--                                (LoanEmiCalculator). Each row's own status transition (PENDING ->
--                                RECOVERED/WAIVED) is this loan's complete recovery history — no
--                                separate ledger table. payroll_arrear_id links a due installment
--                                to the real PayrollArrear the existing payroll-run pipeline
--                                consumes unmodified (com.ewos.payroll.application.
--                                LoanRecoveryService); payroll_run_id/payslip_id/recovered_at are
--                                set once that arrear is actually consumed.

CREATE TABLE employee_loans (
    id                            UUID        PRIMARY KEY,
    tenant_id                     UUID        NOT NULL,
    company_id                    UUID        NOT NULL,
    employee_id                   UUID        NOT NULL REFERENCES employees (id),
    loan_type                     VARCHAR(32) NOT NULL,
    principal_amount              NUMERIC(18,4) NOT NULL,
    annual_interest_rate_percent  NUMERIC(8,4)  NOT NULL DEFAULT 0,
    tenure_months                 INTEGER     NOT NULL,
    disbursed_date                DATE        NOT NULL,
    status                        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    outstanding_principal         NUMERIC(18,4) NOT NULL,
    notes                         VARCHAR(2000),
    closed_at                     TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                    UUID,
    updated_by                    UUID,
    deleted_at                    TIMESTAMPTZ,
    version_no                    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_loans_type
        CHECK (loan_type IN ('PERSONAL_LOAN', 'SALARY_ADVANCE', 'REIMBURSEMENT_RECOVERY', 'OTHER')),
    CONSTRAINT ck_employee_loans_status
        CHECK (status IN ('ACTIVE', 'CLOSED', 'FORECLOSED')),
    CONSTRAINT ck_employee_loans_amounts_nonneg
        CHECK (principal_amount > 0 AND annual_interest_rate_percent >= 0
               AND tenure_months > 0 AND outstanding_principal >= 0)
);

CREATE INDEX ix_employee_loans_tenant_employee_status
    ON employee_loans (tenant_id, employee_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE loan_schedule_installments (
    id                    UUID        PRIMARY KEY,
    loan_id               UUID        NOT NULL REFERENCES employee_loans (id),
    installment_number    INTEGER     NOT NULL,
    emi_amount            NUMERIC(18,4) NOT NULL,
    principal_component   NUMERIC(18,4) NOT NULL,
    interest_component    NUMERIC(18,4) NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    payroll_arrear_id     UUID        REFERENCES payroll_arrears (id),
    payroll_run_id        UUID        REFERENCES payroll_runs (id),
    payslip_id            UUID        REFERENCES payslips (id),
    recovered_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_loan_installments_status
        CHECK (status IN ('PENDING', 'RECOVERED', 'WAIVED')),
    CONSTRAINT ck_loan_installments_number
        CHECK (installment_number >= 1)
);

CREATE UNIQUE INDEX ux_loan_installments_loan_number
    ON loan_schedule_installments (loan_id, installment_number);

CREATE INDEX ix_loan_installments_pending
    ON loan_schedule_installments (loan_id, status)
    WHERE status = 'PENDING';

CREATE INDEX ix_loan_installments_arrear
    ON loan_schedule_installments (payroll_arrear_id)
    WHERE payroll_arrear_id IS NOT NULL;
