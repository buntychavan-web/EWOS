-- Sprint 24L item 3 — Attendance Integration for LOP.
--
-- holidays                    — tenant-wide or company-specific calendar of non-working days,
--                                excluded from attendance-driven loss-of-pay classification.
-- attendance_policies         — new sandwich_leave_policy_enabled opt-in flag: when true, a
--                                weekend/holiday run flanked on both sides by an LOP-triggering
--                                day (absence, half-day, or missing punch) is itself counted as
--                                LOP (com.ewos.attendance.domain.AttendanceLopCalculator).
--
-- Attendance-driven LOP itself introduces no new payroll ledger table: it is computed on demand
-- from the existing immutable time_entries + the new holidays calendar
-- (com.ewos.attendance.application.AttendanceLopService), additive on top of the existing
-- leave-driven LopCalculator, and activates per company only once an AttendancePolicy exists for
-- it (opt-in, backward compatible — see PayrollRunService#processPayslips).

ALTER TABLE attendance_policies
    ADD COLUMN sandwich_leave_policy_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE holidays (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID,
    holiday_date          DATE        NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    recurring_annually    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

-- Two partial unique indexes because company_id is nullable and NULL <> NULL under a plain
-- unique index — this still catches an exact duplicate at either scope.
CREATE UNIQUE INDEX ux_holidays_tenant_company_date_alive
    ON holidays (tenant_id, company_id, holiday_date)
    WHERE deleted_at IS NULL AND company_id IS NOT NULL;

CREATE UNIQUE INDEX ux_holidays_tenant_date_alive
    ON holidays (tenant_id, holiday_date)
    WHERE deleted_at IS NULL AND company_id IS NULL;

CREATE INDEX ix_holidays_tenant_company
    ON holidays (tenant_id, company_id)
    WHERE deleted_at IS NULL;
