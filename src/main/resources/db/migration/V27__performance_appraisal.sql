-- Performance Management (T7 — Volume 5)
--
-- Cycle + template + per-employee appraisal + calibration + bell-curve + increment/promotion
-- recommendation. Sits on the Employee master and reuses the shared Workflow engine for the final
-- calibration approval.

CREATE TABLE performance_cycles (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(256) NOT NULL,
    description              VARCHAR(2000),
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    self_assessment_due      DATE,
    manager_assessment_due   DATE,
    reviewer_assessment_due  DATE,
    calibration_due          DATE,
    bell_curve_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    bell_curve_config_json   VARCHAR(4000),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_perf_cycles_status
        CHECK (status IN ('DRAFT','OPEN','SELF_REVIEW','MANAGER_REVIEW','REVIEWER_REVIEW',
                          'CALIBRATION','CLOSED','CANCELLED')),
    CONSTRAINT ck_perf_cycles_period CHECK (period_end > period_start)
);

CREATE UNIQUE INDEX ux_perf_cycles_code_alive
    ON performance_cycles (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE appraisal_templates (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(2000),
    rating_scale_min      INT         NOT NULL DEFAULT 1,
    rating_scale_max      INT         NOT NULL DEFAULT 5,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appraisal_templates_scale CHECK (rating_scale_max > rating_scale_min)
);

CREATE UNIQUE INDEX ux_appraisal_templates_code_alive
    ON appraisal_templates (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE appraisal_template_sections (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    template_id           UUID        NOT NULL REFERENCES appraisal_templates (id) ON DELETE CASCADE,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(2000),
    weightage             NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    display_order         INT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appr_tmpl_sections_weight CHECK (weightage >= 0 AND weightage <= 100)
);

CREATE INDEX ix_appr_tmpl_sections_template
    ON appraisal_template_sections (tenant_id, template_id)
    WHERE deleted_at IS NULL;

CREATE TABLE appraisals (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    cycle_id                 UUID        NOT NULL REFERENCES performance_cycles (id),
    template_id              UUID        NOT NULL REFERENCES appraisal_templates (id),
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    manager_employee_id      UUID        REFERENCES employees (id),
    reviewer_employee_id     UUID        REFERENCES employees (id),
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING_SELF',
    self_rating              NUMERIC(5,2),
    self_comments            VARCHAR(4000),
    self_submitted_at        TIMESTAMPTZ,
    manager_rating           NUMERIC(5,2),
    manager_comments         VARCHAR(4000),
    manager_submitted_at     TIMESTAMPTZ,
    reviewer_rating          NUMERIC(5,2),
    reviewer_comments        VARCHAR(4000),
    reviewer_submitted_at    TIMESTAMPTZ,
    calibrated_rating        NUMERIC(5,2),
    calibration_notes        VARCHAR(4000),
    calibrated_at            TIMESTAMPTZ,
    calibrated_by            UUID,
    final_rating             NUMERIC(5,2),
    final_band               VARCHAR(32),
    increment_recommendation VARCHAR(32),
    increment_percent        NUMERIC(6,2),
    increment_notes          VARCHAR(2000),
    promotion_recommendation VARCHAR(32),
    promotion_notes          VARCHAR(2000),
    approval_workflow_instance_id UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appraisals_status
        CHECK (status IN ('PENDING_SELF','PENDING_MANAGER','PENDING_REVIEWER','CALIBRATION',
                          'PENDING_APPROVAL','FINALISED','CANCELLED')),
    CONSTRAINT ck_appraisals_increment
        CHECK (increment_recommendation IS NULL
               OR increment_recommendation IN ('NONE','STANDARD','HIGH','WITHHOLD','SPECIAL')),
    CONSTRAINT ck_appraisals_promotion
        CHECK (promotion_recommendation IS NULL
               OR promotion_recommendation IN ('NONE','READY_NOW','READY_NEXT_CYCLE','NOT_READY'))
);

CREATE UNIQUE INDEX ux_appraisals_cycle_employee_alive
    ON appraisals (tenant_id, cycle_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_appraisals_status
    ON appraisals (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_appraisals_cycle_status
    ON appraisals (tenant_id, cycle_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE appraisal_ratings (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    appraisal_id          UUID        NOT NULL REFERENCES appraisals (id) ON DELETE CASCADE,
    section_id            UUID        REFERENCES appraisal_template_sections (id),
    stage                 VARCHAR(32) NOT NULL,
    rating                NUMERIC(5,2) NOT NULL,
    comments              VARCHAR(4000),
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_by           UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_appraisal_ratings_stage
        CHECK (stage IN ('SELF','MANAGER','REVIEWER','CALIBRATED'))
);

CREATE INDEX ix_appraisal_ratings_appraisal
    ON appraisal_ratings (tenant_id, appraisal_id, stage)
    WHERE deleted_at IS NULL;

CREATE TABLE calibration_sessions (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    cycle_id              UUID        NOT NULL REFERENCES performance_cycles (id),
    name                  VARCHAR(256) NOT NULL,
    scheduled_at          TIMESTAMPTZ,
    status                VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    facilitator_id        UUID,
    notes                 VARCHAR(4000),
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_calibration_sessions_status
        CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE INDEX ix_calibration_sessions_cycle
    ON calibration_sessions (tenant_id, cycle_id)
    WHERE deleted_at IS NULL;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PERF_READ',        'View performance cycles, templates, appraisals'),
    (gen_random_uuid(), 'PERF_WRITE',       'Create + edit cycles, templates, appraisals'),
    (gen_random_uuid(), 'PERF_APPRAISE_SELF','Submit self assessment'),
    (gen_random_uuid(), 'PERF_APPRAISE_MANAGER','Submit manager assessment'),
    (gen_random_uuid(), 'PERF_APPRAISE_REVIEWER','Submit reviewer assessment'),
    (gen_random_uuid(), 'PERF_CALIBRATE',   'Run calibration + adjust ratings'),
    (gen_random_uuid(), 'PERF_APPROVE',     'Approve final ratings + increment/promotion'),
    (gen_random_uuid(), 'PERF_ADMIN',       'Administer performance module');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('PERF_READ','PERF_WRITE','PERF_APPRAISE_SELF','PERF_APPRAISE_MANAGER',
     'PERF_APPRAISE_REVIEWER','PERF_CALIBRATE','PERF_APPROVE','PERF_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
