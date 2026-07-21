-- Payroll Bank Processing
--
-- Bank advice batches per finalized payroll run, and per-employee payment instructions with
-- lifecycle tracking (PENDING → PAID / FAILED). Bank details are snapshotted onto the instruction
-- so the trail survives future employee bank-account edits.

-- =====================================================================
-- bank_advices
-- =====================================================================
CREATE TABLE bank_advices (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    payroll_run_id           UUID        NOT NULL REFERENCES payroll_runs (id),
    advice_number            VARCHAR(64) NOT NULL,
    advice_date              DATE        NOT NULL,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    file_format              VARCHAR(32) NOT NULL DEFAULT 'CSV',
    total_count              INTEGER     NOT NULL DEFAULT 0,
    total_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    generated_at             TIMESTAMPTZ,
    generated_by             UUID,
    acknowledged_at          TIMESTAMPTZ,
    acknowledged_by          UUID,
    settled_at               TIMESTAMPTZ,
    notes                    VARCHAR(2048),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_bank_advices_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_bank_advices_status
        CHECK (status IN ('DRAFT','GENERATED','ACKNOWLEDGED','SETTLED','FAILED')),
    CONSTRAINT ck_bank_advices_file_format
        CHECK (file_format IN ('CSV','NACHA','NEFT','SEPA')),
    CONSTRAINT ck_bank_advices_totals_nonneg
        CHECK (total_count >= 0 AND total_amount >= 0),
    CONSTRAINT ck_bank_advices_generated_pair
        CHECK ((status = 'DRAFT')
               OR (generated_at IS NOT NULL AND generated_by IS NOT NULL))
);

CREATE UNIQUE INDEX ux_bank_advices_company_number_alive
    ON bank_advices (tenant_id, company_id, LOWER(advice_number))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_bank_advices_run
    ON bank_advices (tenant_id, payroll_run_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_bank_advices_status
    ON bank_advices (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- payment_instructions — one row per payslip on the advice
-- =====================================================================
CREATE TABLE payment_instructions (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    bank_advice_id           UUID        NOT NULL REFERENCES bank_advices (id) ON DELETE CASCADE,
    payslip_id               UUID        NOT NULL REFERENCES payslips (id),
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    employee_bank_account_id UUID        REFERENCES employee_bank_accounts (id),
    bank_name_snapshot       VARCHAR(256) NOT NULL,
    account_holder_snapshot  VARCHAR(256) NOT NULL,
    account_number_masked    VARCHAR(64) NOT NULL,
    routing_code_snapshot    VARCHAR(64),
    swift_bic_snapshot       VARCHAR(16),
    amount                   NUMERIC(18,4) NOT NULL,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    settlement_reference     VARCHAR(128),
    settled_at               TIMESTAMPTZ,
    failure_reason           VARCHAR(2048),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pmt_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_pmt_status
        CHECK (status IN ('PENDING','PAID','FAILED','SKIPPED')),
    CONSTRAINT ck_pmt_amount_nonneg
        CHECK (amount >= 0),
    CONSTRAINT ck_pmt_paid_pair
        CHECK ((status <> 'PAID') OR (settlement_reference IS NOT NULL AND settled_at IS NOT NULL)),
    CONSTRAINT ck_pmt_failed_pair
        CHECK ((status <> 'FAILED') OR (failure_reason IS NOT NULL))
);

CREATE UNIQUE INDEX ux_pmt_advice_payslip
    ON payment_instructions (bank_advice_id, payslip_id);

CREATE INDEX ix_pmt_status
    ON payment_instructions (tenant_id, status);

CREATE INDEX ix_pmt_employee
    ON payment_instructions (tenant_id, employee_id);
