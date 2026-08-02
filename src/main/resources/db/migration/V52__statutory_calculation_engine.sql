-- Sprint 24H — Statutory Calculation Engine
--
-- Before this migration, every PF/ESI/Professional Tax/LWF/TDS figure Payroll produced was a flat
-- PERCENT_OF_BASIC or FIXED pay component with no rule behind it — no PF wage ceiling, no EPS split,
-- no ESI eligibility threshold, no PT/LWF state slabs, no TDS regime/bracket logic, and employer-side
-- contributions were never computed at all. This migration adds the configurable schema the real
-- engine (application/*CalculationService) reads from — seeded with current Indian statutory law as
-- defaults, fully tenant-overridable, never hardcoded in Java.
--
-- =====================================================================
-- statutory_jurisdictions — lightweight state/country master
-- =====================================================================
CREATE TABLE statutory_jurisdictions (
    id                       UUID        PRIMARY KEY,
    country_code             VARCHAR(2)  NOT NULL,
    state_code               VARCHAR(8)  NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    CONSTRAINT ck_stat_jurisdiction_country_len CHECK (LENGTH(country_code) = 2)
);

CREATE UNIQUE INDEX ux_stat_jurisdictions_code
    ON statutory_jurisdictions (country_code, state_code);

-- India: 28 states + 8 union territories.
INSERT INTO statutory_jurisdictions (id, country_code, state_code, name) VALUES
    (gen_random_uuid(), 'IN', 'AP', 'Andhra Pradesh'),
    (gen_random_uuid(), 'IN', 'AR', 'Arunachal Pradesh'),
    (gen_random_uuid(), 'IN', 'AS', 'Assam'),
    (gen_random_uuid(), 'IN', 'BR', 'Bihar'),
    (gen_random_uuid(), 'IN', 'CG', 'Chhattisgarh'),
    (gen_random_uuid(), 'IN', 'GA', 'Goa'),
    (gen_random_uuid(), 'IN', 'GJ', 'Gujarat'),
    (gen_random_uuid(), 'IN', 'HR', 'Haryana'),
    (gen_random_uuid(), 'IN', 'HP', 'Himachal Pradesh'),
    (gen_random_uuid(), 'IN', 'JH', 'Jharkhand'),
    (gen_random_uuid(), 'IN', 'KA', 'Karnataka'),
    (gen_random_uuid(), 'IN', 'KL', 'Kerala'),
    (gen_random_uuid(), 'IN', 'MP', 'Madhya Pradesh'),
    (gen_random_uuid(), 'IN', 'MH', 'Maharashtra'),
    (gen_random_uuid(), 'IN', 'MN', 'Manipur'),
    (gen_random_uuid(), 'IN', 'ML', 'Meghalaya'),
    (gen_random_uuid(), 'IN', 'MZ', 'Mizoram'),
    (gen_random_uuid(), 'IN', 'NL', 'Nagaland'),
    (gen_random_uuid(), 'IN', 'OD', 'Odisha'),
    (gen_random_uuid(), 'IN', 'PB', 'Punjab'),
    (gen_random_uuid(), 'IN', 'RJ', 'Rajasthan'),
    (gen_random_uuid(), 'IN', 'SK', 'Sikkim'),
    (gen_random_uuid(), 'IN', 'TN', 'Tamil Nadu'),
    (gen_random_uuid(), 'IN', 'TG', 'Telangana'),
    (gen_random_uuid(), 'IN', 'TR', 'Tripura'),
    (gen_random_uuid(), 'IN', 'UP', 'Uttar Pradesh'),
    (gen_random_uuid(), 'IN', 'UK', 'Uttarakhand'),
    (gen_random_uuid(), 'IN', 'WB', 'West Bengal'),
    (gen_random_uuid(), 'IN', 'AN', 'Andaman and Nicobar Islands'),
    (gen_random_uuid(), 'IN', 'CH', 'Chandigarh'),
    (gen_random_uuid(), 'IN', 'DN', 'Dadra and Nagar Haveli and Daman and Diu'),
    (gen_random_uuid(), 'IN', 'DL', 'Delhi'),
    (gen_random_uuid(), 'IN', 'JK', 'Jammu and Kashmir'),
    (gen_random_uuid(), 'IN', 'LA', 'Ladakh'),
    (gen_random_uuid(), 'IN', 'LD', 'Lakshadweep'),
    (gen_random_uuid(), 'IN', 'PY', 'Puducherry');

-- =====================================================================
-- pf_configurations — company_id NULL means the tenant-wide default
-- =====================================================================
CREATE TABLE pf_configurations (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID,
    wage_ceiling             NUMERIC(18,4) NOT NULL,
    eps_wage_ceiling         NUMERIC(18,4) NOT NULL,
    employee_rate_pct        NUMERIC(7,4)  NOT NULL,
    employer_pf_rate_pct     NUMERIC(7,4)  NOT NULL,
    eps_rate_pct             NUMERIC(7,4)  NOT NULL,
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pf_config_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_pf_config_rates CHECK (
        employee_rate_pct >= 0 AND employer_pf_rate_pct >= 0 AND eps_rate_pct >= 0
    )
);

CREATE INDEX ix_pf_config_lookup
    ON pf_configurations (tenant_id, company_id, effective_from DESC)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- esi_configurations
-- =====================================================================
CREATE TABLE esi_configurations (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID,
    wage_threshold           NUMERIC(18,4) NOT NULL,
    employee_rate_pct        NUMERIC(7,4)  NOT NULL,
    employer_rate_pct        NUMERIC(7,4)  NOT NULL,
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_esi_config_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_esi_config_lookup
    ON esi_configurations (tenant_id, company_id, effective_from DESC)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- employee_esi_enrollments — locks ESI coverage for a full contribution
-- period (Apr-Sep / Oct-Mar) once eligibility is established at its
-- start, per the ESI Act — a mid-period raise above the wage threshold
-- must not drop coverage until the period ends.
-- =====================================================================
CREATE TABLE employee_esi_enrollments (
    id                          UUID        PRIMARY KEY,
    tenant_id                   UUID        NOT NULL,
    company_id                  UUID        NOT NULL,
    employee_id                 UUID        NOT NULL REFERENCES employees (id),
    contribution_period_start   DATE        NOT NULL,
    contribution_period_end     DATE        NOT NULL,
    eligible_at_period_start    BOOLEAN     NOT NULL,
    wage_at_period_start        NUMERIC(18,4) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID
);

CREATE UNIQUE INDEX ux_esi_enrollment_period
    ON employee_esi_enrollments (tenant_id, employee_id, contribution_period_start);

-- =====================================================================
-- professional_tax_slabs — state-wise, income-banded
-- =====================================================================
CREATE TABLE professional_tax_slabs (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    jurisdiction_id          UUID        NOT NULL REFERENCES statutory_jurisdictions (id),
    gender                   VARCHAR(16),
    min_monthly_income       NUMERIC(18,4) NOT NULL,
    max_monthly_income       NUMERIC(18,4),
    monthly_tax_amount       NUMERIC(18,4) NOT NULL,
    annual_cap_amount        NUMERIC(18,4),
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_pt_slab_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_pt_slab_income_range CHECK (
        max_monthly_income IS NULL OR max_monthly_income >= min_monthly_income
    ),
    CONSTRAINT ck_pt_slab_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX ix_pt_slab_lookup
    ON professional_tax_slabs (tenant_id, jurisdiction_id, effective_from DESC)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- lwf_configurations — state-wise; remittance_months NULL = every month
-- =====================================================================
CREATE TABLE lwf_configurations (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    jurisdiction_id          UUID        NOT NULL REFERENCES statutory_jurisdictions (id),
    employee_contribution    NUMERIC(18,4) NOT NULL,
    employer_contribution    NUMERIC(18,4) NOT NULL,
    remittance_months        VARCHAR(32),
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_lwf_config_range CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_lwf_config_lookup
    ON lwf_configurations (tenant_id, jurisdiction_id, effective_from DESC)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- income_tax_slabs — progressive brackets per regime + fiscal year
-- =====================================================================
CREATE TABLE income_tax_slabs (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    regime                   VARCHAR(16) NOT NULL,
    fiscal_year              VARCHAR(16) NOT NULL,
    min_income               NUMERIC(18,4) NOT NULL,
    max_income               NUMERIC(18,4),
    rate_pct                 NUMERIC(7,4)  NOT NULL,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_it_slab_regime CHECK (regime IN ('OLD', 'NEW')),
    CONSTRAINT ck_it_slab_income_range CHECK (
        max_income IS NULL OR max_income >= min_income
    )
);

CREATE INDEX ix_it_slab_lookup
    ON income_tax_slabs (tenant_id, regime, fiscal_year, min_income)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- income_tax_policies — non-slab parameters per regime + fiscal year
-- =====================================================================
CREATE TABLE income_tax_policies (
    id                          UUID        PRIMARY KEY,
    tenant_id                   UUID        NOT NULL,
    regime                      VARCHAR(16) NOT NULL,
    fiscal_year                 VARCHAR(16) NOT NULL,
    rebate_income_threshold     NUMERIC(18,4) NOT NULL DEFAULT 0,
    rebate_max_amount           NUMERIC(18,4) NOT NULL DEFAULT 0,
    cess_rate_pct               NUMERIC(7,4)  NOT NULL DEFAULT 0,
    standard_deduction          NUMERIC(18,4) NOT NULL DEFAULT 0,
    chapter_via_max_deduction   NUMERIC(18,4) NOT NULL DEFAULT 0,
    house_property_loss_cap     NUMERIC(18,4) NOT NULL DEFAULT 0,
    active                      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    deleted_at                  TIMESTAMPTZ,
    version_no                  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_it_policy_regime CHECK (regime IN ('OLD', 'NEW'))
);

CREATE UNIQUE INDEX ux_it_policy_active
    ON income_tax_policies (tenant_id, regime, fiscal_year)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- income_tax_surcharge_slabs
-- =====================================================================
CREATE TABLE income_tax_surcharge_slabs (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    regime                   VARCHAR(16) NOT NULL,
    fiscal_year              VARCHAR(16) NOT NULL,
    min_income               NUMERIC(18,4) NOT NULL,
    max_income               NUMERIC(18,4),
    surcharge_rate_pct       NUMERIC(7,4)  NOT NULL,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_it_surcharge_regime CHECK (regime IN ('OLD', 'NEW')),
    CONSTRAINT ck_it_surcharge_income_range CHECK (
        max_income IS NULL OR max_income >= min_income
    )
);

CREATE INDEX ix_it_surcharge_lookup
    ON income_tax_surcharge_slabs (tenant_id, regime, fiscal_year, min_income)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- employee_tax_declarations — per employee, per fiscal year; also the
-- running YTD accumulator the monthly TDS projection reads and updates.
-- =====================================================================
CREATE TABLE employee_tax_declarations (
    id                          UUID        PRIMARY KEY,
    tenant_id                   UUID        NOT NULL,
    company_id                  UUID        NOT NULL,
    employee_id                 UUID        NOT NULL REFERENCES employees (id),
    fiscal_year                 VARCHAR(16) NOT NULL,
    regime                      VARCHAR(16) NOT NULL DEFAULT 'NEW',
    previous_employer_income    NUMERIC(18,4) NOT NULL DEFAULT 0,
    other_income                NUMERIC(18,4) NOT NULL DEFAULT 0,
    house_property_loss         NUMERIC(18,4) NOT NULL DEFAULT 0,
    chapter_via_declared_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    rent_paid_annual            NUMERIC(18,4) NOT NULL DEFAULT 0,
    is_metro_city               BOOLEAN     NOT NULL DEFAULT FALSE,
    lta_exemption_declared      NUMERIC(18,4) NOT NULL DEFAULT 0,
    ytd_taxable_salary          NUMERIC(18,4) NOT NULL DEFAULT 0,
    ytd_hra_received             NUMERIC(18,4) NOT NULL DEFAULT 0,
    ytd_tds_deducted             NUMERIC(18,4) NOT NULL DEFAULT 0,
    ytd_professional_tax_paid    NUMERIC(18,4) NOT NULL DEFAULT 0,
    active                      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    deleted_at                  TIMESTAMPTZ,
    version_no                  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_etd_regime CHECK (regime IN ('OLD', 'NEW'))
);

CREATE UNIQUE INDEX ux_etd_active
    ON employee_tax_declarations (tenant_id, employee_id, fiscal_year)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- gratuity_configurations
-- =====================================================================
CREATE TABLE gratuity_configurations (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID,
    statutory_ceiling        NUMERIC(18,4) NOT NULL,
    rate_numerator           INTEGER     NOT NULL DEFAULT 15,
    rate_denominator         INTEGER     NOT NULL DEFAULT 26,
    min_years_eligibility    NUMERIC(4,2) NOT NULL DEFAULT 5,
    effective_from           DATE        NOT NULL,
    effective_to             DATE,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_gratuity_config_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_gratuity_config_rate CHECK (rate_numerator > 0 AND rate_denominator > 0)
);

CREATE INDEX ix_gratuity_config_lookup
    ON gratuity_configurations (tenant_id, company_id, effective_from DESC)
    WHERE deleted_at IS NULL AND active = TRUE;

-- =====================================================================
-- employee_payroll_profiles — statutory context needed by the engine
-- =====================================================================
ALTER TABLE employee_payroll_profiles
    ADD COLUMN state_code VARCHAR(8),
    ADD COLUMN is_international_worker BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN vpf_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN vpf_percentage NUMERIC(7,4) NOT NULL DEFAULT 0;

-- =====================================================================
-- statutory_deductions — employer contribution now genuinely computed;
-- add the EPS carve-out so PF challans can report EPS separately from
-- the employer's residual EPF share, as Indian PF returns require.
-- =====================================================================
ALTER TABLE statutory_deductions
    ADD COLUMN eps_contribution NUMERIC(18,4) NOT NULL DEFAULT 0;

-- =====================================================================
-- final_settlements — gratuity is now calculated by GratuityCalculationService
-- by default; these columns record when and why an operator overrode either
-- the calculated amount or the 5-year continuous-service eligibility rule
-- (only waivable for death/disablement per the Payment of Gratuity Act).
-- =====================================================================
ALTER TABLE final_settlements
    ADD COLUMN gratuity_calculated_amount NUMERIC(18,4),
    ADD COLUMN gratuity_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN gratuity_override_reason VARCHAR(512),
    ADD COLUMN gratuity_eligibility_waived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN gratuity_waiver_reason VARCHAR(64);

ALTER TABLE final_settlements
    ADD CONSTRAINT ck_fs_gratuity_waiver_reason CHECK (
        gratuity_waiver_reason IS NULL
        OR gratuity_waiver_reason IN ('DEATH', 'DISABLEMENT')
    );

ALTER TABLE final_settlements
    ADD CONSTRAINT ck_fs_gratuity_override_needs_reason CHECK (
        NOT gratuity_overridden OR gratuity_override_reason IS NOT NULL
    );

-- =====================================================================
-- Seed data — current Indian statutory law as of FY 2025-26, scoped to
-- the platform's bootstrap tenant. These are defaults, not hardcoded
-- business logic: every value is editable via the statutory-config API,
-- and any other tenant configures its own from a clean slate.
-- =====================================================================

-- PF: 12% employee, 12% employer (of which 8.33% EPS + 3.67% employer EPF
-- share), wage ceiling ₹15,000/month for both PF and EPS.
INSERT INTO pf_configurations
    (id, tenant_id, company_id, wage_ceiling, eps_wage_ceiling,
     employee_rate_pct, employer_pf_rate_pct, eps_rate_pct, effective_from)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', NULL,
     15000, 15000, 12, 12, 8.33, '2025-04-01');

-- ESI: 0.75% employee, 3.25% employer, eligibility wage threshold ₹21,000/month.
INSERT INTO esi_configurations
    (id, tenant_id, company_id, wage_threshold, employee_rate_pct, employer_rate_pct, effective_from)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', NULL,
     21000, 0.75, 3.25, '2025-04-01');

-- Gratuity: 15/26 x last drawn basic x completed years, capped at the
-- current statutory ceiling of ₹20,00,000, 5-year minimum continuous service.
INSERT INTO gratuity_configurations
    (id, tenant_id, company_id, statutory_ceiling, rate_numerator, rate_denominator,
     min_years_eligibility, effective_from)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', NULL,
     2000000, 15, 26, 5, '2025-04-01');

-- Professional Tax — Karnataka and Maharashtra as representative seeded states
-- (statutory annual cap ₹2,500; every other state starts unconfigured until an
-- admin adds its slabs).
INSERT INTO professional_tax_slabs
    (id, tenant_id, jurisdiction_id, gender, min_monthly_income, max_monthly_income,
     monthly_tax_amount, annual_cap_amount, effective_from)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, NULL, 0, 24999, 0, 2500, '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'KA'
UNION ALL
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, NULL, 25000, NULL, 200, 2500, '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'KA'
UNION ALL
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, NULL, 0, 7499, 0, 2500, '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'MH'
UNION ALL
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, NULL, 7500, 9999, 175, 2500, '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'MH'
UNION ALL
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, NULL, 10000, NULL, 200, 2500, '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'MH';

-- Labour Welfare Fund — Karnataka and Maharashtra as representative seeded
-- states (half-yearly remittance in the closing months of each half-year).
INSERT INTO lwf_configurations
    (id, tenant_id, jurisdiction_id, employee_contribution, employer_contribution,
     remittance_months, effective_from)
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, 20, 40, '12', '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'KA'
UNION ALL
SELECT gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, j.id, 12, 36, '6,12', '2025-04-01'::date
FROM statutory_jurisdictions j WHERE j.country_code = 'IN' AND j.state_code = 'MH';

-- Income tax — FY 2025-26 slabs, both regimes.
INSERT INTO income_tax_slabs (id, tenant_id, regime, fiscal_year, min_income, max_income, rate_pct) VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 0, 400000, 0),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 400000, 800000, 5),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 800000, 1200000, 10),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 1200000, 1600000, 15),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 1600000, 2000000, 20),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 2000000, 2400000, 25),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 2400000, NULL, 30),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 0, 250000, 0),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 250000, 500000, 5),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 500000, 1000000, 20),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 1000000, NULL, 30);

INSERT INTO income_tax_policies
    (id, tenant_id, regime, fiscal_year, rebate_income_threshold, rebate_max_amount,
     cess_rate_pct, standard_deduction, chapter_via_max_deduction, house_property_loss_cap)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26',
     1200000, 60000, 4, 75000, 0, 200000),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26',
     500000, 12500, 4, 50000, 150000, 200000);

INSERT INTO income_tax_surcharge_slabs
    (id, tenant_id, regime, fiscal_year, min_income, max_income, surcharge_rate_pct)
VALUES
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 5000000, 10000000, 10),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 10000000, 20000000, 15),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'NEW', '2025-26', 20000000, NULL, 25),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 5000000, 10000000, 10),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 10000000, 20000000, 15),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 20000000, 50000000, 25),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'OLD', '2025-26', 50000000, NULL, 37);
