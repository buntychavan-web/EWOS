-- Employee Onboarding (T5 — Volume 5)
--
-- Layered on the T4 pre-boarding surface. Introduces:
--
--   * onboarding_plans           — one per newly-joined employee; drives the post-joining
--                                  activities.
--   * onboarding_task_templates  — reusable per-company catalogue of onboarding tasks.
--   * onboarding_task_instances  — the actual tasks on a plan.
--   * onboarding_surveys         — 30/60/90-day + open-ended feedback captured against a plan.
--
-- A plan is auto-created by the {@code offer.preboarding.checklist.joined} listener when
-- a T4 pre-boarding checklist reaches JOINED. The listener also copies the source offer id +
-- joining date so post-joining reporting can chain the full hire → offer → onboard trail.

-- =====================================================================
-- Onboarding task templates
-- =====================================================================
CREATE TABLE onboarding_task_templates (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    code              VARCHAR(64) NOT NULL,
    name              VARCHAR(256) NOT NULL,
    description       VARCHAR(2000),
    task_type         VARCHAR(32) NOT NULL,
    sort_order        INT         NOT NULL DEFAULT 0,
    mandatory         BOOLEAN     NOT NULL DEFAULT TRUE,
    default_owner     VARCHAR(16) NOT NULL DEFAULT 'HR',
    default_sla_days  INT,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_onboarding_task_type
        CHECK (task_type IN (
            'ORIENTATION','DOCUMENT_UPLOAD','POLICY_ACKNOWLEDGEMENT','TRAINING',
            'INTRODUCTION_MEETING','TEAM_INTRO','SYSTEM_ACCESS','WORKSTATION_SETUP',
            'HANDBOOK','SURVEY','GOAL_SETTING','BUDDY_ASSIGNMENT','OTHER')),
    CONSTRAINT ck_onboarding_task_owner
        CHECK (default_owner IN ('EMPLOYEE','MANAGER','BUDDY','HR','IT','ADMIN','OTHER'))
);

CREATE UNIQUE INDEX ux_onboarding_task_templates_code_alive
    ON onboarding_task_templates (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_onboarding_task_templates_active
    ON onboarding_task_templates (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Onboarding plans (one per new hire)
-- =====================================================================
CREATE TABLE onboarding_plans (
    id                     UUID        PRIMARY KEY,
    tenant_id              UUID        NOT NULL,
    company_id             UUID        NOT NULL,
    employee_id            UUID        NOT NULL REFERENCES employees (id),
    source_offer_id        UUID        REFERENCES offers (id),
    source_checklist_id    UUID        REFERENCES preboarding_checklists (id),
    joining_date           DATE,
    manager_employee_id    UUID        REFERENCES employees (id),
    buddy_employee_id      UUID        REFERENCES employees (id),
    status                 VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    completion_percent     NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    started_at             TIMESTAMPTZ,
    completed_at           TIMESTAMPTZ,
    completed_by           UUID,
    notes                  VARCHAR(4000),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    deleted_at             TIMESTAMPTZ,
    version_no             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_onboarding_plans_status
        CHECK (status IN ('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_onboarding_plans_completion
        CHECK (completion_percent >= 0 AND completion_percent <= 100)
);

CREATE UNIQUE INDEX ux_onboarding_plans_employee_alive
    ON onboarding_plans (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_onboarding_plans_status
    ON onboarding_plans (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_onboarding_plans_joining
    ON onboarding_plans (tenant_id, company_id, joining_date)
    WHERE deleted_at IS NULL AND joining_date IS NOT NULL;

CREATE INDEX ix_onboarding_plans_manager
    ON onboarding_plans (tenant_id, manager_employee_id)
    WHERE deleted_at IS NULL AND manager_employee_id IS NOT NULL;

-- =====================================================================
-- Onboarding task instances
-- =====================================================================
CREATE TABLE onboarding_task_instances (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    plan_id           UUID        NOT NULL REFERENCES onboarding_plans (id),
    template_id       UUID        REFERENCES onboarding_task_templates (id),
    name              VARCHAR(256) NOT NULL,
    task_type         VARCHAR(32) NOT NULL,
    owner             VARCHAR(16) NOT NULL DEFAULT 'HR',
    assigned_employee_id UUID     REFERENCES employees (id),
    mandatory         BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order        INT         NOT NULL DEFAULT 0,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    due_date          DATE,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID,
    external_ref      VARCHAR(512),
    result_json       TEXT,
    notes             VARCHAR(4000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_onboarding_task_instances_task_type
        CHECK (task_type IN (
            'ORIENTATION','DOCUMENT_UPLOAD','POLICY_ACKNOWLEDGEMENT','TRAINING',
            'INTRODUCTION_MEETING','TEAM_INTRO','SYSTEM_ACCESS','WORKSTATION_SETUP',
            'HANDBOOK','SURVEY','GOAL_SETTING','BUDDY_ASSIGNMENT','OTHER')),
    CONSTRAINT ck_onboarding_task_instances_owner
        CHECK (owner IN ('EMPLOYEE','MANAGER','BUDDY','HR','IT','ADMIN','OTHER')),
    CONSTRAINT ck_onboarding_task_instances_status
        CHECK (status IN (
            'PENDING','IN_PROGRESS','WAITING_ON_EMPLOYEE','WAITING_ON_COMPANY',
            'COMPLETED','SKIPPED','FAILED'))
);

CREATE INDEX ix_onboarding_task_instances_plan
    ON onboarding_task_instances (tenant_id, plan_id, sort_order)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_onboarding_task_instances_status
    ON onboarding_task_instances (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_onboarding_task_instances_due
    ON onboarding_task_instances (tenant_id, due_date)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL
      AND status NOT IN ('COMPLETED','SKIPPED');

-- =====================================================================
-- Onboarding surveys
-- =====================================================================
CREATE TABLE onboarding_surveys (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    plan_id           UUID        NOT NULL REFERENCES onboarding_plans (id),
    survey_type       VARCHAR(32) NOT NULL,
    responses_json    TEXT,
    overall_rating    NUMERIC(4,2),
    comments          VARCHAR(8000),
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_onboarding_surveys_type
        CHECK (survey_type IN ('DAY_1','WEEK_1','DAY_30','DAY_60','DAY_90','EXIT_ONBOARDING')),
    CONSTRAINT ck_onboarding_surveys_rating
        CHECK (overall_rating IS NULL OR (overall_rating >= 0 AND overall_rating <= 10))
);

CREATE UNIQUE INDEX ux_onboarding_surveys_type_alive
    ON onboarding_surveys (tenant_id, plan_id, survey_type)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'ONBOARDING_READ',   'View onboarding plans, tasks, surveys'),
    (gen_random_uuid(), 'ONBOARDING_WRITE',  'Manage onboarding tasks, plans, buddy / manager assignment'),
    (gen_random_uuid(), 'ONBOARDING_SURVEY', 'Submit onboarding surveys'),
    (gen_random_uuid(), 'ONBOARDING_ADMIN',  'Administer onboarding task templates and configuration');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('ONBOARDING_READ','ONBOARDING_WRITE','ONBOARDING_SURVEY','ONBOARDING_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
