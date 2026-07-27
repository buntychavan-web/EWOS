-- Sprint 4 — Enterprise Workflow Engine extensions (WP-006 follow-on)
--
-- Extends the existing metadata-driven workflow engine (V11) with capabilities needed by
-- multi-level enterprise approval flows, without altering any existing column or breaking any
-- definition authored under V11:
--   * Effective-dating on definitions (effective_from/effective_to), alongside the existing
--     definition_version — lets a tenant schedule a new version to take over on a future date.
--   * Per-state approval_mode (SINGLE/ANY/ALL) — enables parallel approval steps. Defaults to
--     SINGLE, which reproduces today's behaviour exactly for every existing definition.
--   * Per-state default_approver_role — lets a state declare a dynamic approver role
--     (MANAGER/HOD/HR/FINANCE/CEO/CUSTOM:<code>) resolved at task-assignment time instead of the
--     caller always supplying a concrete actor id.
--   * escalation_level/escalated_at on tasks — the workflow_tasks.status CHECK constraint has
--     allowed 'ESCALATED' since V11; this finally gives the engine the bookkeeping to use it.
--   * workflow_delegations — a user can delegate their own task inbox to another user for a
--     bounded time window (task delegation, explicitly deferred in the V11 module notes).
--   * notifications — a minimal in-app notification inbox, the delivery mechanism for
--     TASK_ASSIGNED / TASK_ESCALATED / INSTANCE_COMPLETED events.

-- =====================================================================
-- workflow_definitions — effective dating
-- =====================================================================
ALTER TABLE workflow_definitions
    ADD COLUMN effective_from TIMESTAMPTZ,
    ADD COLUMN effective_to   TIMESTAMPTZ;

ALTER TABLE workflow_definitions
    ADD CONSTRAINT ck_wf_defs_effective_window
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from);

-- =====================================================================
-- workflow_states — parallel approval + dynamic approver
-- =====================================================================
ALTER TABLE workflow_states
    ADD COLUMN approval_mode         VARCHAR(16) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN default_approver_role VARCHAR(64);

ALTER TABLE workflow_states
    ADD CONSTRAINT ck_wf_states_approval_mode
        CHECK (approval_mode IN ('SINGLE', 'ANY', 'ALL'));

-- =====================================================================
-- workflow_tasks — escalation bookkeeping
-- =====================================================================
ALTER TABLE workflow_tasks
    ADD COLUMN escalation_level INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN escalated_at     TIMESTAMPTZ;

ALTER TABLE workflow_tasks
    ADD CONSTRAINT ck_wf_tasks_escalation_level CHECK (escalation_level >= 0);

-- =====================================================================
-- workflow_delegations
-- =====================================================================
CREATE TABLE workflow_delegations (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    delegator_actor_id  UUID        NOT NULL,
    delegate_actor_id   UUID        NOT NULL,
    role_code           VARCHAR(64),
    starts_at           TIMESTAMPTZ NOT NULL,
    ends_at             TIMESTAMPTZ NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    notes               VARCHAR(1024),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_wf_delegations_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_wf_delegations_not_self CHECK (delegator_actor_id <> delegate_actor_id)
);

CREATE INDEX ix_wf_delegations_delegator_active
    ON workflow_delegations (tenant_id, delegator_actor_id, active, starts_at, ends_at)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- notifications — minimal in-app inbox
-- =====================================================================
CREATE TABLE notifications (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    recipient_actor_id  UUID        NOT NULL,
    type                VARCHAR(32) NOT NULL,
    title               VARCHAR(256) NOT NULL,
    body                VARCHAR(2048),
    link                VARCHAR(512),
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX ix_notifications_recipient_unread
    ON notifications (tenant_id, recipient_actor_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE INDEX ix_notifications_recipient_all
    ON notifications (tenant_id, recipient_actor_id, created_at DESC);
