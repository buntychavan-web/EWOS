-- Statutory Compliance
--
-- Per-payslip statutory-deduction snapshot, and per-jurisdiction / per-month remittance challan
-- that aggregates the deductions from every payslip in the window. Snapshotted amounts so
-- historical filings remain reproducible even if pay components are edited later.

-- =====================================================================
-- statutory_deductions
-- =====================================================================
CREATE TABLE statutory_deductions (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    payroll_run_id           UUID        NOT NULL REFERENCES payroll_runs (id),
    payslip_id               UUID        NOT NULL REFERENCES payslips (id),
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    jurisdiction             VARCHAR(16) NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    period_month             INTEGER     NOT NULL,
    taxable_base             NUMERIC(18,4) NOT NULL DEFAULT 0,
    employee_contribution    NUMERIC(18,4) NOT NULL DEFAULT 0,
    employer_contribution    NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    statutory_challan_id     UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_stat_ded_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_stat_ded_period
        CHECK (period_month BETWEEN 190001 AND 300012),
    CONSTRAINT ck_stat_ded_amounts_nonneg
        CHECK (taxable_base >= 0 AND employee_contribution >= 0
               AND employer_contribution >= 0 AND total_amount >= 0)
);

CREATE UNIQUE INDEX ux_stat_ded_payslip_code
    ON statutory_deductions (payslip_id, LOWER(code));

CREATE INDEX ix_stat_ded_run
    ON statutory_deductions (tenant_id, payroll_run_id);

CREATE INDEX ix_stat_ded_employee_month
    ON statutory_deductions (tenant_id, employee_id, period_month DESC);

CREATE INDEX ix_stat_ded_lookup
    ON statutory_deductions (tenant_id, jurisdiction, code, period_month);

CREATE INDEX ix_stat_ded_challan
    ON statutory_deductions (statutory_challan_id)
    WHERE statutory_challan_id IS NOT NULL;

-- =====================================================================
-- statutory_challans — monthly remittance per (jurisdiction, code, month, company)
-- =====================================================================
CREATE TABLE statutory_challans (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    jurisdiction             VARCHAR(16) NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    period_month             INTEGER     NOT NULL,
    total_employees          INTEGER     NOT NULL DEFAULT 0,
    total_taxable_base       NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_employee_contribution NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_employer_contribution NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_amount             NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency                 VARCHAR(3)  NOT NULL DEFAULT 'USD',
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    filed_at                 TIMESTAMPTZ,
    filed_by                 UUID,
    filing_reference         VARCHAR(128),
    paid_at                  TIMESTAMPTZ,
    paid_by                  UUID,
    payment_reference        VARCHAR(128),
    notes                    VARCHAR(2048),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_challan_currency_len
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_challan_period
        CHECK (period_month BETWEEN 190001 AND 300012),
    CONSTRAINT ck_challan_status
        CHECK (status IN ('DRAFT','FILED','PAID','CANCELLED')),
    CONSTRAINT ck_challan_amounts_nonneg
        CHECK (total_employees >= 0 AND total_taxable_base >= 0
               AND total_employee_contribution >= 0
               AND total_employer_contribution >= 0 AND total_amount >= 0),
    CONSTRAINT ck_challan_filed_pair
        CHECK ((status = 'DRAFT' OR status = 'CANCELLED')
               OR (filed_at IS NOT NULL AND filed_by IS NOT NULL AND filing_reference IS NOT NULL)),
    CONSTRAINT ck_challan_paid_pair
        CHECK ((status <> 'PAID')
               OR (paid_at IS NOT NULL AND paid_by IS NOT NULL AND payment_reference IS NOT NULL))
);

CREATE UNIQUE INDEX ux_challan_scope_alive
    ON statutory_challans (tenant_id, company_id, jurisdiction, LOWER(code), period_month)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_challan_status
    ON statutory_challans (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Wire the FK on statutory_deductions after statutory_challans exists.
-- =====================================================================
ALTER TABLE statutory_deductions
    ADD CONSTRAINT fk_stat_ded_challan
    FOREIGN KEY (statutory_challan_id) REFERENCES statutory_challans (id);
