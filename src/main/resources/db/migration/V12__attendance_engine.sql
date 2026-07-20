-- WP-007 Attendance Engine
--
-- Per-employee time capture and period-scoped timesheet approval. Consumes:
--   * Employee module (WP-005)  — every entry belongs to an employee
--   * Workflow module (WP-006)  — timesheet submissions kick off a workflow instance
--
-- Aggregates
--   attendance_policies   Per-tenant metadata (hours per day, working days, grace period).
--   time_entries          Raw clock events: IN / OUT / BREAK_START / BREAK_END.
--   timesheets            Period-scoped rollup per (employee, period_start, period_end)
--                         with approval status driven by workflow.
--
-- Notes
--   * All tables are tenant-scoped and soft-deleted (except time_entries — those are the
--     immutable time record; corrections happen via new entries not by mutating old ones).
--   * timesheets are optimistic-locked so competing submit-vs-approve calls fail loudly.

-- =====================================================================
-- attendance_policies
-- =====================================================================
CREATE TABLE attendance_policies (
    id                        UUID        PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    company_id                UUID,
    code                      VARCHAR(64) NOT NULL,
    name                      VARCHAR(128) NOT NULL,
    description               VARCHAR(512),
    standard_hours_per_day    NUMERIC(4,2) NOT NULL DEFAULT 8.00,
    standard_hours_per_week   NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    working_days              VARCHAR(64)  NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    grace_minutes             INTEGER      NOT NULL DEFAULT 5,
    overtime_multiplier       NUMERIC(4,2) NOT NULL DEFAULT 1.50,
    period_length_days        INTEGER      NOT NULL DEFAULT 7,
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_by                UUID,
    deleted_at                TIMESTAMPTZ,
    version_no                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_att_policies_hours_positive
        CHECK (standard_hours_per_day > 0 AND standard_hours_per_week > 0),
    CONSTRAINT ck_att_policies_grace_nonneg
        CHECK (grace_minutes >= 0),
    CONSTRAINT ck_att_policies_period_positive
        CHECK (period_length_days BETWEEN 1 AND 31),
    CONSTRAINT ck_att_policies_ot_multiplier
        CHECK (overtime_multiplier >= 1.0)
);

CREATE UNIQUE INDEX ux_att_policies_tenant_code_alive
    ON attendance_policies (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_att_policies_tenant_company_active
    ON attendance_policies (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- time_entries
-- =====================================================================
CREATE TABLE time_entries (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    company_id     UUID        NOT NULL,
    employee_id    UUID        NOT NULL REFERENCES employees (id),
    event_type     VARCHAR(32) NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    source         VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    location       VARCHAR(256),
    notes          VARCHAR(1024),
    correction_of  UUID        REFERENCES time_entries (id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version_no     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_time_entries_event_type
        CHECK (event_type IN ('IN','OUT','BREAK_START','BREAK_END')),
    CONSTRAINT ck_time_entries_source
        CHECK (source IN ('MANUAL','KIOSK','MOBILE','BADGE','SYSTEM','CORRECTION'))
);

CREATE INDEX ix_time_entries_tenant_employee_time
    ON time_entries (tenant_id, employee_id, occurred_at DESC);

CREATE INDEX ix_time_entries_tenant_time
    ON time_entries (tenant_id, occurred_at DESC);

CREATE INDEX ix_time_entries_correction_of
    ON time_entries (correction_of)
    WHERE correction_of IS NOT NULL;

-- =====================================================================
-- timesheets
-- =====================================================================
CREATE TABLE timesheets (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    policy_id                UUID        REFERENCES attendance_policies (id),
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    worked_hours             NUMERIC(6,2) NOT NULL DEFAULT 0,
    overtime_hours           NUMERIC(6,2) NOT NULL DEFAULT 0,
    break_hours              NUMERIC(6,2) NOT NULL DEFAULT 0,
    absence_hours            NUMERIC(6,2) NOT NULL DEFAULT 0,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at             TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    approved_by              UUID,
    rejected_at              TIMESTAMPTZ,
    rejected_by              UUID,
    rejection_reason         VARCHAR(2048),
    workflow_instance_id     UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_timesheets_status
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_timesheets_period_range
        CHECK (period_end >= period_start),
    CONSTRAINT ck_timesheets_hours_nonneg
        CHECK (worked_hours >= 0 AND overtime_hours >= 0 AND break_hours >= 0 AND absence_hours >= 0),
    CONSTRAINT ck_timesheets_submitted_has_ts
        CHECK ((status <> 'DRAFT') = (submitted_at IS NOT NULL) OR status = 'CANCELLED'),
    CONSTRAINT ck_timesheets_approved_pair
        CHECK ((status = 'APPROVED') = (approved_at IS NOT NULL AND approved_by IS NOT NULL)),
    CONSTRAINT ck_timesheets_rejected_pair
        CHECK ((status = 'REJECTED') = (rejected_at IS NOT NULL AND rejected_by IS NOT NULL))
);

CREATE UNIQUE INDEX ux_timesheets_employee_period_alive
    ON timesheets (employee_id, period_start, period_end)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_timesheets_tenant_status
    ON timesheets (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_timesheets_tenant_period
    ON timesheets (tenant_id, period_start, period_end)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_timesheets_workflow_instance
    ON timesheets (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL AND deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'ATT_READ',     'Read attendance policies, time entries, and timesheets'),
    (gen_random_uuid(), 'ATT_WRITE',    'Record time entries and edit draft timesheets'),
    (gen_random_uuid(), 'ATT_APPROVE',  'Approve or reject submitted timesheets'),
    (gen_random_uuid(), 'ATT_ADMIN',    'Manage attendance policies and delete timesheets');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ATT_READ','ATT_WRITE','ATT_APPROVE','ATT_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
