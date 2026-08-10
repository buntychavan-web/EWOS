-- Sprint 27D reconciliation — carry-forward support for leave_accrual_entries.
--
-- Approved Sprint 27D v1 baseline (decision 7 + 14): year-end carry-forward is in scope, must be
-- idempotent and auditable through the same ledger as monthly accrual, and each ledger row must
-- carry a "reason/source" so a monthly accrual entry and a carry-forward entry are distinguishable.
--
-- entry_type is that discriminator. A carry-forward entry is stored with accrual_month = 1
-- (January) — the real calendar month the carried-forward balance lands in at the year boundary,
-- per decision 8's calendar-year leave year; this is not a sentinel value in the sense the approved
-- baseline warns against (an arbitrary non-calendar marker like 0 or 13), but it IS a real month
-- that can also carry a genuine MONTHLY_ACCRUAL row for the same employee/type/year. The original
-- ux_leave_accrual_employee_type_period index did not scope by entry_type, so it would have wrongly
-- treated those two different, legitimate rows as the same period and rejected the second insert.
-- It is therefore narrowed here into a partial index scoped to entry_type = 'MONTHLY_ACCRUAL',
-- preserving the exact same (employee, leave_type, year, month) idempotency key and behavior for
-- monthly accrual rows while no longer colliding with carry-forward rows. Carry-forward gets its
-- own partial unique index keyed by (employee, leave_type, year), scoped to
-- entry_type = 'CARRY_FORWARD', giving it independent DB-level idempotency the same way.
ALTER TABLE leave_accrual_entries
    ADD COLUMN entry_type VARCHAR(32) NOT NULL DEFAULT 'MONTHLY_ACCRUAL';

ALTER TABLE leave_accrual_entries
    ALTER COLUMN entry_type DROP DEFAULT;

ALTER TABLE leave_accrual_entries
    ADD CONSTRAINT ck_leave_accrual_entry_type
        CHECK (entry_type IN ('MONTHLY_ACCRUAL', 'CARRY_FORWARD'));

DROP INDEX ux_leave_accrual_employee_type_period;

CREATE UNIQUE INDEX ux_leave_accrual_employee_type_period
    ON leave_accrual_entries (employee_id, leave_type_id, accrual_year, accrual_month)
    WHERE entry_type = 'MONTHLY_ACCRUAL';

CREATE UNIQUE INDEX ux_leave_accrual_carry_forward_employee_type_year
    ON leave_accrual_entries (employee_id, leave_type_id, accrual_year)
    WHERE entry_type = 'CARRY_FORWARD';
