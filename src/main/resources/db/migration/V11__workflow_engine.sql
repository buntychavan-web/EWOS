-- WP-006 Workflow Engine
--
-- Metadata-driven state-machine orchestration platform. Every downstream module that needs an
-- approval or lifecycle flow (leave, expense, hire, timesheet, ...) registers a workflow
-- definition here and drives its instances instead of hard-coding transitions in code.
--
-- Aggregates
--   workflow_definitions   — versioned per-tenant definition (subject_type + code + version)
--   workflow_states        — nodes; one initial state, zero or more terminal states
--   workflow_transitions   — directed edges with an action code + optional guard expression
--   workflow_instances     — a specific run of a definition against a subject entity
--   workflow_tasks         — human tasks emitted when a state requires an actor decision
--   workflow_history       — append-only audit of every transition (never soft-deleted)
--
-- Design notes
--   * All rows carry tenant_id (NOT NULL, no FK yet — Tenant module deferred).
--   * Everything except workflow_history is soft-deletable and optimistic-locked.
--   * workflow_history is append-only — no soft delete, no @Version, no updates.
--   * A definition version is immutable once instances start running against it; a new
--     version bumps `version_no` and the code + version pair becomes the new unique key.
--   * Actions are metadata (strings), not enum, so tenants can add "escalate", "clarify",
--     "hold", etc. without a schema change.

-- =====================================================================
-- workflow_definitions
-- =====================================================================
CREATE TABLE workflow_definitions (
    id               UUID        PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    code             VARCHAR(128) NOT NULL,
    name             VARCHAR(256) NOT NULL,
    description      VARCHAR(2048),
    subject_type     VARCHAR(128) NOT NULL,
    definition_version INTEGER   NOT NULL DEFAULT 1,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    version_no       BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_wf_defs_tenant_code_version_alive
    ON workflow_definitions (tenant_id, LOWER(code), definition_version)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_wf_defs_tenant_subject
    ON workflow_definitions (tenant_id, subject_type)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- workflow_states
-- =====================================================================
CREATE TABLE workflow_states (
    id               UUID        PRIMARY KEY,
    definition_id    UUID        NOT NULL REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    code             VARCHAR(64) NOT NULL,
    name             VARCHAR(128) NOT NULL,
    is_initial       BOOLEAN     NOT NULL DEFAULT FALSE,
    is_terminal      BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order       INTEGER     NOT NULL DEFAULT 100,
    sla_hours        INTEGER,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version_no       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_states_sla_positive CHECK (sla_hours IS NULL OR sla_hours > 0)
);

CREATE UNIQUE INDEX ux_wf_states_def_code
    ON workflow_states (definition_id, LOWER(code));

CREATE UNIQUE INDEX ux_wf_states_def_initial
    ON workflow_states (definition_id)
    WHERE is_initial = TRUE;

-- =====================================================================
-- workflow_transitions
-- =====================================================================
CREATE TABLE workflow_transitions (
    id               UUID        PRIMARY KEY,
    definition_id    UUID        NOT NULL REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    from_state_id    UUID        NOT NULL REFERENCES workflow_states (id) ON DELETE CASCADE,
    to_state_id      UUID        NOT NULL REFERENCES workflow_states (id) ON DELETE CASCADE,
    action_code      VARCHAR(64) NOT NULL,
    required_role    VARCHAR(64),
    auto             BOOLEAN     NOT NULL DEFAULT FALSE,
    guard_expression VARCHAR(2048),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID,
    version_no       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_trans_no_self_loop_unless_auto
        CHECK (from_state_id <> to_state_id OR auto = TRUE)
);

CREATE UNIQUE INDEX ux_wf_transitions_def_from_action
    ON workflow_transitions (definition_id, from_state_id, LOWER(action_code));

CREATE INDEX ix_wf_transitions_def
    ON workflow_transitions (definition_id);

-- =====================================================================
-- workflow_instances
-- =====================================================================
CREATE TABLE workflow_instances (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID        NOT NULL,
    definition_id       UUID        NOT NULL REFERENCES workflow_definitions (id),
    subject_type        VARCHAR(128) NOT NULL,
    subject_id          UUID        NOT NULL,
    current_state_id    UUID        NOT NULL REFERENCES workflow_states (id),
    status              VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    correlation_key     VARCHAR(256),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_instances_status
        CHECK (status IN ('RUNNING','COMPLETED','CANCELLED','ERROR')),
    CONSTRAINT ck_wf_instances_completed_has_ts
        CHECK ((status IN ('COMPLETED','CANCELLED','ERROR')) = (completed_at IS NOT NULL))
);

CREATE INDEX ix_wf_instances_tenant_status
    ON workflow_instances (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_wf_instances_subject
    ON workflow_instances (tenant_id, subject_type, subject_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_wf_instances_current_state
    ON workflow_instances (current_state_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_wf_instances_correlation
    ON workflow_instances (tenant_id, correlation_key)
    WHERE correlation_key IS NOT NULL AND deleted_at IS NULL;

-- =====================================================================
-- workflow_tasks
-- =====================================================================
CREATE TABLE workflow_tasks (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    instance_id           UUID        NOT NULL REFERENCES workflow_instances (id) ON DELETE CASCADE,
    state_id              UUID        NOT NULL REFERENCES workflow_states (id),
    assignee_actor_type   VARCHAR(32) NOT NULL,
    assignee_actor_id     UUID,
    assignee_role_code    VARCHAR(64),
    action_code           VARCHAR(64),
    due_at                TIMESTAMPTZ,
    status                VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    completed_at          TIMESTAMPTZ,
    completed_by          UUID,
    outcome_code          VARCHAR(64),
    notes                 VARCHAR(2048),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_tasks_status
        CHECK (status IN ('OPEN','CLAIMED','COMPLETED','CANCELLED','ESCALATED')),
    CONSTRAINT ck_wf_tasks_actor_type
        CHECK (assignee_actor_type IN ('USER','EMPLOYEE','ROLE','SYSTEM')),
    CONSTRAINT ck_wf_tasks_actor_or_role
        CHECK ((assignee_actor_id IS NOT NULL) OR (assignee_role_code IS NOT NULL)),
    CONSTRAINT ck_wf_tasks_completed_has_ts
        CHECK ((status IN ('COMPLETED','CANCELLED')) = (completed_at IS NOT NULL))
);

CREATE INDEX ix_wf_tasks_open_by_actor
    ON workflow_tasks (tenant_id, assignee_actor_id, status)
    WHERE deleted_at IS NULL AND status IN ('OPEN','CLAIMED');

CREATE INDEX ix_wf_tasks_open_by_role
    ON workflow_tasks (tenant_id, assignee_role_code, status)
    WHERE deleted_at IS NULL AND status IN ('OPEN','CLAIMED') AND assignee_role_code IS NOT NULL;

CREATE INDEX ix_wf_tasks_instance
    ON workflow_tasks (instance_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_wf_tasks_due
    ON workflow_tasks (tenant_id, due_at)
    WHERE deleted_at IS NULL AND status IN ('OPEN','CLAIMED');

-- =====================================================================
-- workflow_history — append-only
-- =====================================================================
CREATE TABLE workflow_history (
    id               UUID        PRIMARY KEY,
    instance_id      UUID        NOT NULL REFERENCES workflow_instances (id) ON DELETE CASCADE,
    from_state_id    UUID        REFERENCES workflow_states (id),
    to_state_id      UUID        NOT NULL REFERENCES workflow_states (id),
    action_code      VARCHAR(64) NOT NULL,
    actor_id         UUID,
    task_id          UUID        REFERENCES workflow_tasks (id),
    notes            VARCHAR(2048),
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_wf_history_instance_time
    ON workflow_history (instance_id, occurred_at DESC);

CREATE INDEX ix_wf_history_actor
    ON workflow_history (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'WF_READ',   'Read workflow definitions, instances, and tasks'),
    (gen_random_uuid(), 'WF_WRITE',  'Author workflow definitions and start instances'),
    (gen_random_uuid(), 'WF_ADMIN',  'Manage workflow definitions and cancel instances'),
    (gen_random_uuid(), 'WF_ACT',    'Complete workflow tasks (approve, reject, delegate)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('WF_READ','WF_WRITE','WF_ADMIN','WF_ACT')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
