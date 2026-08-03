-- Sprint 24K — Payroll Version 1 mandatory domain enhancements.
--
-- 1. pay_components.recurring — distinguishes a component that repeats every payroll (Basic, HRA,
--    standard allowances) from a one-time/variable one (bonus, incentive, ex-gratia). Needed so the
--    income-tax engine can annualise the recurring salary correctly instead of multiplying a
--    one-time bonus by 12 (see PayrollRunService / IncomeTaxCalculationService for the consumer).
--    Defaults TRUE so every existing component keeps today's (recurring) behaviour untouched.
ALTER TABLE pay_components ADD COLUMN recurring BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. employee_tax_declarations — a second YTD accumulator, tracking tax recovered specifically
--    against one-time/variable payments (Sprint 24K §8.3), kept separate from ytd_tds_deducted
--    (which stays recurring-salary-only) so a bonus's incremental tax never distorts the recurring
--    monthly TDS trajectory for future periods.
ALTER TABLE employee_tax_declarations
    ADD COLUMN ytd_variable_payment_tds_recovered NUMERIC(18,4) NOT NULL DEFAULT 0;

-- 3. tds_adjustment_log — append-only audit trail for every non-standard TDS recovery decision:
--    a shortfall caused by capping recovery against actual payable earnings (§8.2), or an
--    incremental recovery caused by a one-time payment changing the projected tax slab/surcharge/
--    cess (§8.3). Never updated or deleted — this is the audit history the sprint requires.
CREATE TABLE tds_adjustment_log (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    payroll_run_id           UUID        NOT NULL REFERENCES payroll_runs (id),
    payslip_id               UUID        REFERENCES payslips (id),
    period_month             INTEGER     NOT NULL,
    adjustment_type          VARCHAR(32) NOT NULL,
    expected_recovery        NUMERIC(18,4) NOT NULL DEFAULT 0,
    actual_recovery          NUMERIC(18,4) NOT NULL DEFAULT 0,
    shortfall_amount         NUMERIC(18,4) NOT NULL DEFAULT 0,
    cumulative_ytd_shortfall NUMERIC(18,4) NOT NULL DEFAULT 0,
    notes                    VARCHAR(2000),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tds_adj_type CHECK (adjustment_type IN ('SHORTFALL_CAP', 'VARIABLE_PAYMENT_INCREMENTAL'))
);

CREATE INDEX ix_tds_adj_employee_period ON tds_adjustment_log (tenant_id, employee_id, period_month);
CREATE INDEX ix_tds_adj_run ON tds_adjustment_log (payroll_run_id);

-- =====================================================================
-- LTA Block Management (Sprint 24K §8.1)
-- =====================================================================
-- Section 10(5) of the Income Tax Act exempts LTA for 2 journeys performed in a block of 4
-- calendar years, with the blocks fixed by the government (not the financial year) and one unused
-- journey from a block eligible to carry forward into the first calendar year of the next block.
-- The exact current block boundaries are a government-fixed fact, not a code constant this engine
-- can safely hardcode — see docs/business-rules/payroll-domain-enhancements.md for the
-- statutory-verification flag on this exact point. `lta_block_configurations` makes the block
-- start year and duration fully configurable per tenant/company so this can be corrected the
-- moment a specific block's boundary needs confirming, without a code change.

CREATE TABLE lta_block_configurations (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID,
    block_duration_years  INTEGER     NOT NULL DEFAULT 4,
    anchor_block_start_year INTEGER   NOT NULL,
    max_exempt_claims_per_block INTEGER NOT NULL DEFAULT 2,
    carry_forward_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    carry_forward_max_claims INTEGER  NOT NULL DEFAULT 1,
    effective_from        DATE        NOT NULL,
    effective_to          DATE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_lta_block_duration CHECK (block_duration_years > 0),
    CONSTRAINT ck_lta_max_claims CHECK (max_exempt_claims_per_block > 0)
);

-- Seed the DEFAULT tenant with the commonly-cited current block (2022-2025) so the engine is
-- usable out of the box; docs/business-rules flags this as needing statutory confirmation before
-- go-live rather than asserting it is authoritative.
INSERT INTO lta_block_configurations
    (id, tenant_id, company_id, block_duration_years, anchor_block_start_year,
     max_exempt_claims_per_block, carry_forward_enabled, carry_forward_max_claims,
     effective_from, active, notes)
VALUES (
    '00000000-0000-0000-0006-000000000001',
    '00000000-0000-0000-0000-000000000001',
    NULL,
    4,
    2022,
    2,
    TRUE,
    1,
    '2022-01-01',
    TRUE,
    'Seeded default per commonly-cited 2022-2025 LTA block under Section 10(5). CONFIRM against the current CBDT/government notification before relying on this in production — see docs/business-rules/payroll-domain-enhancements.md.'
) ON CONFLICT DO NOTHING;

-- employee_lta_block_claims — append-only ledger. A row per claim event (never updated), so block
-- history is never erased by a financial-year close, only ever added to. Balances (claimed count,
-- remaining tax-free claims, closing balance) are derived by summing this table, not stored
-- mutable state, matching this codebase's existing append-only-history convention.
CREATE TABLE employee_lta_block_claims (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    lta_block_configuration_id UUID   NOT NULL REFERENCES lta_block_configurations (id),
    block_start_year      INTEGER     NOT NULL,
    block_end_year        INTEGER     NOT NULL,
    claim_type            VARCHAR(24) NOT NULL,
    fiscal_year           VARCHAR(16) NOT NULL,
    claim_date            DATE        NOT NULL,
    lta_credited_amount   NUMERIC(18,4) NOT NULL DEFAULT 0,
    amount_claimed        NUMERIC(18,4) NOT NULL DEFAULT 0,
    tax_free_amount       NUMERIC(18,4) NOT NULL DEFAULT 0,
    taxable_amount        NUMERIC(18,4) NOT NULL DEFAULT 0,
    carried_forward_from_previous_block BOOLEAN NOT NULL DEFAULT FALSE,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    CONSTRAINT ck_lta_claim_type CHECK (claim_type IN ('ANNUAL_CREDIT', 'JOURNEY_CLAIM', 'BLOCK_CARRY_FORWARD'))
);

CREATE INDEX ix_lta_claims_employee_block
    ON employee_lta_block_claims (tenant_id, employee_id, block_start_year, block_end_year);
