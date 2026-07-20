-- WP-008 Leave Engine
--
-- Per-tenant leave types (metadata), employee allocations (yearly quotas), employee balances
-- (accrual / consumed tally), and leave requests (period + type + workflow-driven approval).
--
-- Aggregates
--   leave_types           metadata dictionary (VACATION, SICK, PERSONAL, ...)
--   leave_allocations     effective-dated per-employee quota per type per year
--   leave_balances        running balance per employee per type per year
--   leave_requests        approval-driven leave submission

-- =====================================================================
-- leave_types
-- =====================================================================
CREATE TABLE leave_types (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    code                     VARCHAR(64) NOT NULL,
    name                     VARCHAR(128) NOT NULL,
    description              VARCHAR(512),
    paid                     BOOLEAN     NOT NULL DEFAULT TRUE,
    accrual_days_per_year    NUMERIC(6,2) NOT NULL DEFAULT 0,
    max_balance_days         NUMERIC(6,2),
    carry_forward_days       NUMERIC(6,2) NOT NULL DEFAULT 0,
    requires_approval        BOOLEAN     NOT NULL DEFAULT TRUE,
    min_notice_days          INTEGER     NOT NULL DEFAULT 0,
    active                   BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order               INTEGER     NOT NULL DEFAULT 100,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_types_accrual_nonneg
        CHECK (accrual_days_per_year >= 0 AND carry_forward_days >= 0),
    CONSTRAINT ck_leave_types_min_notice_nonneg
        CHECK (min_notice_days >= 0),
    CONSTRAINT ck_leave_types_max_balance_nonneg
        CHECK (max_balance_days IS NULL OR max_balance_days >= 0)
);

CREATE UNIQUE INDEX ux_leave_types_tenant_code_alive
    ON leave_types (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_leave_types_tenant_active
    ON leave_types (tenant_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- leave_allocations
-- =====================================================================
CREATE TABLE leave_allocations (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    employee_id       UUID        NOT NULL REFERENCES employees (id),
    leave_type_id     UUID        NOT NULL REFERENCES leave_types (id),
    year              INTEGER     NOT NULL,
    allocated_days    NUMERIC(6,2) NOT NULL,
    notes             VARCHAR(1024),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_allocations_year_range
        CHECK (year BETWEEN 1900 AND 3000),
    CONSTRAINT ck_leave_allocations_days_nonneg
        CHECK (allocated_days >= 0)
);

CREATE UNIQUE INDEX ux_leave_allocations_emp_type_year_alive
    ON leave_allocations (employee_id, leave_type_id, year)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_leave_allocations_tenant_year
    ON leave_allocations (tenant_id, year)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- leave_balances
-- =====================================================================
CREATE TABLE leave_balances (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    employee_id       UUID        NOT NULL REFERENCES employees (id),
    leave_type_id     UUID        NOT NULL REFERENCES leave_types (id),
    year              INTEGER     NOT NULL,
    accrued_days      NUMERIC(6,2) NOT NULL DEFAULT 0,
    consumed_days     NUMERIC(6,2) NOT NULL DEFAULT 0,
    pending_days      NUMERIC(6,2) NOT NULL DEFAULT 0,
    adjustment_days   NUMERIC(6,2) NOT NULL DEFAULT 0,
    carry_forward_days NUMERIC(6,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_balances_year_range
        CHECK (year BETWEEN 1900 AND 3000),
    CONSTRAINT ck_leave_balances_days_nonneg
        CHECK (accrued_days >= 0 AND consumed_days >= 0 AND pending_days >= 0
               AND carry_forward_days >= 0)
);

CREATE UNIQUE INDEX ux_leave_balances_emp_type_year_alive
    ON leave_balances (employee_id, leave_type_id, year)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_leave_balances_tenant_year
    ON leave_balances (tenant_id, year)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- leave_requests
-- =====================================================================
CREATE TABLE leave_requests (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    employee_id              UUID        NOT NULL REFERENCES employees (id),
    leave_type_id            UUID        NOT NULL REFERENCES leave_types (id),
    start_date               DATE        NOT NULL,
    end_date                 DATE        NOT NULL,
    days_requested           NUMERIC(6,2) NOT NULL,
    reason                   VARCHAR(2048),
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at             TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    approved_by              UUID,
    rejected_at              TIMESTAMPTZ,
    rejected_by              UUID,
    rejection_reason         VARCHAR(2048),
    cancelled_at             TIMESTAMPTZ,
    cancelled_by             UUID,
    workflow_instance_id     UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_requests_status
        CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT ck_leave_requests_date_range
        CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_requests_days_positive
        CHECK (days_requested > 0),
    CONSTRAINT ck_leave_requests_approved_pair
        CHECK ((status = 'APPROVED') = (approved_at IS NOT NULL AND approved_by IS NOT NULL)),
    CONSTRAINT ck_leave_requests_rejected_pair
        CHECK ((status = 'REJECTED') = (rejected_at IS NOT NULL AND rejected_by IS NOT NULL))
);

CREATE INDEX ix_leave_requests_tenant_status
    ON leave_requests (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_leave_requests_employee_period
    ON leave_requests (tenant_id, employee_id, start_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_leave_requests_workflow
    ON leave_requests (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL AND deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'LEAVE_READ',    'Read leave types, allocations, balances, and requests'),
    (gen_random_uuid(), 'LEAVE_WRITE',   'Submit and edit own leave requests'),
    (gen_random_uuid(), 'LEAVE_APPROVE', 'Approve or reject leave requests'),
    (gen_random_uuid(), 'LEAVE_ADMIN',   'Manage leave types, allocations, and balances');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('LEAVE_READ','LEAVE_WRITE','LEAVE_APPROVE','LEAVE_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
