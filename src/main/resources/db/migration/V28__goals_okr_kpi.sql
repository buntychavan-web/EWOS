-- Goals / OKR / KPI (T8 — Volume 5)
--
-- Reusable goal library + assigned goals scoped to individual / team / department / company, with
-- KRA / KPI / OKR classification, weightage, progress tracking, and review workflow.

CREATE TABLE goal_library (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(2000),
    goal_type             VARCHAR(16) NOT NULL,
    category              VARCHAR(64),
    default_weightage     NUMERIC(5,2) NOT NULL DEFAULT 0,
    default_target        VARCHAR(256),
    unit_of_measure       VARCHAR(64),
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_goal_library_type CHECK (goal_type IN ('KRA','KPI','OKR')),
    CONSTRAINT ck_goal_library_weight CHECK (default_weightage >= 0 AND default_weightage <= 100)
);

CREATE UNIQUE INDEX ux_goal_library_code_alive
    ON goal_library (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE goals (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    library_goal_id          UUID        REFERENCES goal_library (id),
    parent_goal_id           UUID        REFERENCES goals (id),
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(256) NOT NULL,
    description              VARCHAR(2000),
    goal_type                VARCHAR(16) NOT NULL,
    scope                    VARCHAR(16) NOT NULL,
    employee_id              UUID        REFERENCES employees (id),
    org_unit_id              UUID,
    performance_cycle_id     UUID,
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    weightage                NUMERIC(5,2) NOT NULL DEFAULT 0,
    target                   VARCHAR(256),
    unit_of_measure          VARCHAR(64),
    current_value            VARCHAR(256),
    progress_percent         NUMERIC(5,2) NOT NULL DEFAULT 0,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority                 VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    review_score             NUMERIC(5,2),
    review_notes             VARCHAR(4000),
    reviewed_at              TIMESTAMPTZ,
    reviewed_by              UUID,
    closed_at                TIMESTAMPTZ,
    closed_by                UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_goals_type CHECK (goal_type IN ('KRA','KPI','OKR')),
    CONSTRAINT ck_goals_scope CHECK (scope IN ('INDIVIDUAL','TEAM','DEPARTMENT','COMPANY')),
    CONSTRAINT ck_goals_status
        CHECK (status IN ('DRAFT','ASSIGNED','IN_PROGRESS','UNDER_REVIEW','COMPLETED','CANCELLED')),
    CONSTRAINT ck_goals_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_goals_weight CHECK (weightage >= 0 AND weightage <= 100),
    CONSTRAINT ck_goals_progress CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CONSTRAINT ck_goals_period CHECK (period_end > period_start)
);

CREATE UNIQUE INDEX ux_goals_code_alive
    ON goals (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_goals_employee
    ON goals (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_goals_status
    ON goals (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_goals_scope
    ON goals (tenant_id, company_id, scope, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_goals_cycle
    ON goals (tenant_id, performance_cycle_id)
    WHERE deleted_at IS NULL AND performance_cycle_id IS NOT NULL;

CREATE TABLE goal_progress_updates (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    goal_id               UUID        NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    current_value         VARCHAR(256),
    progress_percent      NUMERIC(5,2) NOT NULL,
    notes                 VARCHAR(4000),
    recorded_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recorded_by           UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_goal_progress_percent CHECK (progress_percent >= 0 AND progress_percent <= 100)
);

CREATE INDEX ix_goal_progress_goal
    ON goal_progress_updates (tenant_id, goal_id, recorded_at DESC)
    WHERE deleted_at IS NULL;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'GOAL_READ',   'View goal library and assigned goals'),
    (gen_random_uuid(), 'GOAL_WRITE',  'Create + assign + update goals'),
    (gen_random_uuid(), 'GOAL_UPDATE_PROGRESS','Record progress updates'),
    (gen_random_uuid(), 'GOAL_REVIEW', 'Review + score goals'),
    (gen_random_uuid(), 'GOAL_ADMIN',  'Administer goal library');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('GOAL_READ','GOAL_WRITE','GOAL_UPDATE_PROGRESS','GOAL_REVIEW','GOAL_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
