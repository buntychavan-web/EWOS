-- Interview Management (T3 — Volume 5)
--
-- Layers interview lifecycle on top of the T2 ATS surface. Introduces:
--
--   * interview_templates      — reusable per-tenant round configs (type + duration + scorecard
--                                schema JSON). Application services can spawn a round from a
--                                template to avoid re-entering shape data.
--   * interview_rounds         — a scheduled interview attached to a specific
--                                job_applications row. Carries schedule + status + decision that
--                                feeds the application pipeline.
--   * interview_participants   — panel — one row per interviewer / observer / coordinator.
--   * interview_scorecards     — structured post-interview evaluation from each interviewer.
--   * candidate_interview_feedback — candidate's own feedback on the interview experience.
--
-- All tables are per-tenant + soft-deletable + version-locked. Rounds are uniquely numbered
-- within an application. Scorecards are unique per (round, interviewer) so re-submits update in
-- place. Notifications and calendar integrations are pluggable — no rows are stored for them.

-- =====================================================================
-- Interview templates
-- =====================================================================
CREATE TABLE interview_templates (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID        NOT NULL,
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(256) NOT NULL,
    description         VARCHAR(2000),
    interview_type      VARCHAR(32) NOT NULL,
    default_duration_minutes INT    NOT NULL DEFAULT 60,
    scorecard_schema    TEXT,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_interview_templates_type
        CHECK (interview_type IN (
            'PHONE_SCREEN','TECHNICAL','BEHAVIORAL','PANEL','HR','EXECUTIVE',
            'TAKE_HOME','LIVE_CODING','ONSITE','FINAL','OTHER')),
    CONSTRAINT ck_interview_templates_duration
        CHECK (default_duration_minutes > 0 AND default_duration_minutes <= 1440)
);

CREATE UNIQUE INDEX ux_interview_templates_company_code_alive
    ON interview_templates (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_interview_templates_active
    ON interview_templates (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Interview rounds (one per scheduled interview per application)
-- =====================================================================
CREATE TABLE interview_rounds (
    id                     UUID        PRIMARY KEY,
    tenant_id              UUID        NOT NULL,
    company_id             UUID        NOT NULL,
    application_id         UUID        NOT NULL REFERENCES job_applications (id),
    template_id            UUID        REFERENCES interview_templates (id),
    round_number           INT         NOT NULL,
    name                   VARCHAR(256) NOT NULL,
    interview_type         VARCHAR(32) NOT NULL,
    duration_minutes       INT         NOT NULL,
    mode                   VARCHAR(16) NOT NULL,
    location               VARCHAR(512),
    meeting_url            VARCHAR(1024),
    scheduled_start        TIMESTAMPTZ,
    scheduled_end          TIMESTAMPTZ,
    actual_start           TIMESTAMPTZ,
    actual_end             TIMESTAMPTZ,
    status                 VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    decision               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_notes         VARCHAR(4000),
    decided_at             TIMESTAMPTZ,
    decided_by             UUID,
    coordinator_employee_id UUID       REFERENCES employees (id),
    external_calendar_ref  VARCHAR(512),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    deleted_at             TIMESTAMPTZ,
    version_no             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_interview_rounds_type
        CHECK (interview_type IN (
            'PHONE_SCREEN','TECHNICAL','BEHAVIORAL','PANEL','HR','EXECUTIVE',
            'TAKE_HOME','LIVE_CODING','ONSITE','FINAL','OTHER')),
    CONSTRAINT ck_interview_rounds_mode
        CHECK (mode IN ('VIDEO','IN_PERSON','PHONE','HYBRID')),
    CONSTRAINT ck_interview_rounds_status
        CHECK (status IN (
            'DRAFT','SCHEDULED','RESCHEDULED','IN_PROGRESS','COMPLETED',
            'CANCELLED','NO_SHOW','PENDING_FEEDBACK')),
    CONSTRAINT ck_interview_rounds_decision
        CHECK (decision IN ('PENDING','PROCEED','HOLD','REJECT')),
    CONSTRAINT ck_interview_rounds_round_number
        CHECK (round_number >= 1),
    CONSTRAINT ck_interview_rounds_duration
        CHECK (duration_minutes > 0 AND duration_minutes <= 1440),
    CONSTRAINT ck_interview_rounds_schedule
        CHECK (scheduled_end IS NULL OR scheduled_start IS NULL
               OR scheduled_end > scheduled_start),
    CONSTRAINT ck_interview_rounds_actual
        CHECK (actual_end IS NULL OR actual_start IS NULL OR actual_end > actual_start)
);

CREATE UNIQUE INDEX ux_interview_rounds_number_alive
    ON interview_rounds (tenant_id, application_id, round_number)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_interview_rounds_application
    ON interview_rounds (tenant_id, application_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_interview_rounds_status
    ON interview_rounds (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_interview_rounds_scheduled
    ON interview_rounds (tenant_id, company_id, scheduled_start)
    WHERE deleted_at IS NULL AND scheduled_start IS NOT NULL;

-- =====================================================================
-- Interview participants (panel members)
-- =====================================================================
CREATE TABLE interview_participants (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    round_id            UUID        NOT NULL REFERENCES interview_rounds (id),
    employee_id         UUID        NOT NULL REFERENCES employees (id),
    role                VARCHAR(32) NOT NULL,
    attendance          VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    external_calendar_ref VARCHAR(512),
    notes               VARCHAR(2000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_interview_participants_role
        CHECK (role IN ('INTERVIEWER','PANEL_LEAD','OBSERVER','COORDINATOR')),
    CONSTRAINT ck_interview_participants_attendance
        CHECK (attendance IN ('UNKNOWN','ATTENDED','ABSENT','PARTIAL'))
);

CREATE UNIQUE INDEX ux_interview_participants_unique
    ON interview_participants (tenant_id, round_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_interview_participants_employee
    ON interview_participants (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Interview scorecards (per interviewer per round)
-- =====================================================================
CREATE TABLE interview_scorecards (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    round_id          UUID        NOT NULL REFERENCES interview_rounds (id),
    interviewer_id    UUID        NOT NULL REFERENCES employees (id),
    overall_rating    NUMERIC(4,2),
    recommendation    VARCHAR(32) NOT NULL,
    strengths         VARCHAR(4000),
    weaknesses        VARCHAR(4000),
    comments          VARCHAR(8000),
    criteria_json     TEXT,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_scorecards_recommendation
        CHECK (recommendation IN (
            'STRONG_HIRE','HIRE','LEAN_HIRE','LEAN_NO_HIRE','NO_HIRE','STRONG_NO_HIRE','NO_DECISION')),
    CONSTRAINT ck_scorecards_rating
        CHECK (overall_rating IS NULL OR (overall_rating >= 0 AND overall_rating <= 10))
);

CREATE UNIQUE INDEX ux_scorecards_round_interviewer_alive
    ON interview_scorecards (tenant_id, round_id, interviewer_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_scorecards_round
    ON interview_scorecards (tenant_id, round_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Candidate feedback (candidate's take on the interview)
-- =====================================================================
CREATE TABLE candidate_interview_feedback (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    round_id          UUID        NOT NULL REFERENCES interview_rounds (id),
    candidate_id      UUID        NOT NULL REFERENCES candidates (id),
    rating_experience NUMERIC(4,2),
    rating_process    NUMERIC(4,2),
    would_reapply     BOOLEAN,
    comments          VARCHAR(8000),
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_feedback_ratings
        CHECK ((rating_experience IS NULL OR (rating_experience >= 0 AND rating_experience <= 10))
           AND (rating_process    IS NULL OR (rating_process    >= 0 AND rating_process    <= 10)))
);

CREATE UNIQUE INDEX ux_candidate_feedback_round_alive
    ON candidate_interview_feedback (tenant_id, round_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'INTERVIEW_READ',            'View interview templates, rounds, panels, scorecards'),
    (gen_random_uuid(), 'INTERVIEW_WRITE',           'Create / schedule / reschedule / cancel interviews and manage panels'),
    (gen_random_uuid(), 'INTERVIEW_SUBMIT_SCORECARD','Submit scorecards for an interview round'),
    (gen_random_uuid(), 'INTERVIEW_ADMIN',           'Administer interview configuration; hard-delete data');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('INTERVIEW_READ','INTERVIEW_WRITE','INTERVIEW_SUBMIT_SCORECARD','INTERVIEW_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
