-- Codex CTO audit P0-4: nothing stopped two REGULAR payroll runs from being started against the
-- same period concurrently (the application-level pre-check added alongside this migration is not
-- itself race-proof under concurrent requests). This partial unique index is the authoritative
-- guard: at most one non-FAILED REGULAR run may exist per (tenant, payroll_period) at a time.
-- FAILED runs are excluded so a failed attempt can be retried with a fresh REGULAR run.
-- SUPPLEMENTARY and FINAL_SETTLEMENT runs are untouched — many of those are expected per period.
CREATE UNIQUE INDEX ux_payroll_runs_one_regular_active_per_period
    ON payroll_runs (tenant_id, payroll_period_id)
    WHERE deleted_at IS NULL AND run_type = 'REGULAR' AND status <> 'FAILED';
