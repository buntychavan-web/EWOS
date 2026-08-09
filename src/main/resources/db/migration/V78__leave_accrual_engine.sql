-- Sprint 27D — Leave accrual engine (post Sprint 27C product audit, gap #3).
--
-- Prior to this migration, LeaveBalance.accrued_days was only ever set by an admin's manual
-- POST /api/v1/leave/allocations call (LeaveBalanceService.upsertAllocation), which OVERWRITES
-- the full year's allocation in one shot. LeaveType.accrual_days_per_year was a configured field
-- nothing ever read to actually credit balances incrementally over time.
--
-- leave_accrual_entries is the append-only ledger the new LeaveAccrualService/LeaveAccrualJob
-- write to: one row per (employee, leave_type, accrual_year, accrual_month), recording both the
-- uncapped pro-rata request and the amount actually credited (which may be less, once
-- LeaveType.max_balance_days is reached). The unique index is the idempotency guard — a second
-- attempt to accrue the same employee/type/period is a no-op by construction (checked in code
-- before insert; enforced here as the defense-in-depth backstop, same pattern this codebase uses
-- everywhere else for idempotent ledger writes).
CREATE TABLE leave_accrual_entries (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    company_id        UUID          NOT NULL,
    employee_id       UUID          NOT NULL REFERENCES employees (id),
    leave_type_id     UUID          NOT NULL REFERENCES leave_types (id),
    leave_balance_id  UUID          NOT NULL REFERENCES leave_balances (id),
    accrual_year      INT           NOT NULL,
    accrual_month     INT           NOT NULL,
    requested_days    NUMERIC(6,2)  NOT NULL,
    credited_days     NUMERIC(6,2)  NOT NULL,
    capped            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    version_no        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_leave_accrual_month_range
        CHECK (accrual_month BETWEEN 1 AND 12),
    CONSTRAINT ck_leave_accrual_days_nonneg
        CHECK (requested_days >= 0 AND credited_days >= 0),
    CONSTRAINT ck_leave_accrual_credited_le_requested
        CHECK (credited_days <= requested_days)
);

CREATE UNIQUE INDEX ux_leave_accrual_employee_type_period
    ON leave_accrual_entries (employee_id, leave_type_id, accrual_year, accrual_month);

-- Backs LeaveAccrualJob's bulk sweep query (all entries for a given period, across employees).
CREATE INDEX ix_leave_accrual_tenant_period
    ON leave_accrual_entries (tenant_id, accrual_year, accrual_month);
