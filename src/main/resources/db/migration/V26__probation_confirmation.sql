-- Probation & Confirmation (T6 — Volume 5)
--
-- Adds probation lifecycle on top of the Employee master. On onboarding-plan COMPLETED, a
-- ProbationRecord can be materialised for the employee; the record tracks period, extensions,
-- manager review, HR recommendation, and confirmation outcome.

CREATE TABLE probation_policies (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(2000),
    default_period_days   INT         NOT NULL,
    max_extension_days    INT         NOT NULL DEFAULT 0,
    allow_early_confirm   BOOLEAN     NOT NULL DEFAULT FALSE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_probation_policies_period
        CHECK (default_period_days > 0 AND default_period_days <= 1095),
    CONSTRAINT ck_probation_policies_extension
        CHECK (max_extension_days >= 0)
);

CREATE UNIQUE INDEX ux_probation_policies_code_alive
    ON probation_policies (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE probation_records (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    policy_id                UUID        REFERENCES probation_policies (id),
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    extended_end             DATE,
    extension_reason         VARCHAR(2000),
    status                   VARCHAR(32) NOT NULL DEFAULT 'IN_PROBATION',
    manager_review_notes     VARCHAR(4000),
    manager_review_at        TIMESTAMPTZ,
    manager_review_by        UUID,
    hr_recommendation        VARCHAR(32),
    hr_recommendation_notes  VARCHAR(4000),
    hr_recommended_at        TIMESTAMPTZ,
    hr_recommended_by        UUID,
    approval_workflow_instance_id UUID,
    confirmed_at             TIMESTAMPTZ,
    confirmed_by             UUID,
    confirmation_letter_uri  VARCHAR(1024),
    terminated_at            TIMESTAMPTZ,
    terminated_by            UUID,
    outcome_notes            VARCHAR(4000),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_probation_records_status
        CHECK (status IN (
            'IN_PROBATION','EXTENDED','PENDING_APPROVAL','CONFIRMED','TERMINATED','CANCELLED')),
    CONSTRAINT ck_probation_records_recommendation
        CHECK (hr_recommendation IS NULL OR hr_recommendation IN (
            'CONFIRM','EXTEND','TERMINATE')),
    CONSTRAINT ck_probation_records_period
        CHECK (period_end > period_start),
    CONSTRAINT ck_probation_records_extended
        CHECK (extended_end IS NULL OR extended_end > period_end)
);

CREATE UNIQUE INDEX ux_probation_records_employee_alive
    ON probation_records (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_probation_records_status
    ON probation_records (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_probation_records_due
    ON probation_records (tenant_id, company_id, COALESCE(extended_end, period_end))
    WHERE deleted_at IS NULL AND status IN ('IN_PROBATION','EXTENDED');

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PROBATION_READ',        'View probation records + policies'),
    (gen_random_uuid(), 'PROBATION_WRITE',       'Create + extend + manage probation records'),
    (gen_random_uuid(), 'PROBATION_RECOMMEND',   'Record HR / manager recommendation'),
    (gen_random_uuid(), 'PROBATION_APPROVE',     'Approve confirmation / termination'),
    (gen_random_uuid(), 'PROBATION_ADMIN',       'Administer probation policies');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('PROBATION_READ','PROBATION_WRITE','PROBATION_RECOMMEND','PROBATION_APPROVE','PROBATION_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
