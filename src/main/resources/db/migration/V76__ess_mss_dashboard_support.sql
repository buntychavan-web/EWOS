-- Sprint 27C — ESS/MSS completion (Profile Self-Service, Notification Inbox).
--
-- NOTE on scope vs. the original PRD draft: the PRD's V76 draft also proposed adding a
-- "company_id" column to a table it called "attendance_holidays" for the Upcoming Calendar
-- feature. Verified against the actual schema before writing this migration: no such table
-- exists. The real holiday-calendar table (added in V65__attendance_lop_integration.sql,
-- Sprint 24L) is named "holidays", and it already has a nullable company_id column, two partial
-- unique indexes correctly handling the tenant-wide-vs-company-specific semantics, and a
-- recurring_annually flag with matching logic in Holiday.fallsOn(). There is nothing to add —
-- see HolidayRepository.findEffectiveForCompany, reused as-is by EssCalendarService. This
-- migration is intentionally narrower than the original draft as a result (confirmed with the
-- requester before implementation).

-- Employee self-service profile extensions (additive, idempotent).
ALTER TABLE employees
    ADD COLUMN IF NOT EXISTS emergency_contact_name   VARCHAR(200),
    ADD COLUMN IF NOT EXISTS emergency_contact_phone  VARCHAR(50),
    ADD COLUMN IF NOT EXISTS avatar_storage_uri       VARCHAR(500);

-- Notification inbox soft-delete support (additive).
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Matches the ix_ naming convention already used by this table's own indexes above
-- (ix_notifications_recipient_unread / ix_notifications_recipient_all, V41), which this
-- migration deliberately leaves untouched rather than dropping/renaming.
CREATE INDEX IF NOT EXISTS ix_notifications_recipient_active
    ON notifications (tenant_id, recipient_actor_id, deleted_at, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_notifications_recipient_unread_alive
    ON notifications (tenant_id, recipient_actor_id, deleted_at, read_at)
    WHERE deleted_at IS NULL;
