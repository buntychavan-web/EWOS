-- Sprint 26 — Exit Management V1, increment 2.
--
-- Notice period had only buyout (pay to skip notice) before this migration. Adds the remaining
-- notice-period actions: recovery (recover pay from the employee for a shortfall — the opposite
-- direction of buyout), waiver (drop the requirement entirely), garden leave (a paid,
-- non-working window inside the notice period), extension (push the notice end date out), and
-- early release (approve an earlier-than-scheduled last day).
ALTER TABLE resignations
    ADD COLUMN notice_recovery_amount NUMERIC(14,2),
    ADD COLUMN notice_waived          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notice_waiver_reason   VARCHAR(2000),
    ADD COLUMN garden_leave_start_date DATE,
    ADD COLUMN garden_leave_end_date   DATE,
    ADD COLUMN notice_extension_reason VARCHAR(2000),
    ADD COLUMN early_release_reason    VARCHAR(2000);
