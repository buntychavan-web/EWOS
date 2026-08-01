-- Sprint 24B — Bulk appraisal cycle launch + strict cycle state machine + transition audit trail.
--
-- 1) Extends performance_cycles.status with three new lifecycle stages (HR_REVIEW,
--    FINAL_APPROVAL, RELEASED) sitting between the existing CALIBRATION and CLOSED stages, so the
--    enterprise lifecycle requested in Sprint 24B (Draft -> Published -> Self Review -> Manager
--    Review -> Reviewer Review -> HR Review -> Final Approval -> Released -> Closed) is fully
--    representable. This is additive only: DRAFT/OPEN/SELF_REVIEW/MANAGER_REVIEW/
--    REVIEWER_REVIEW/CALIBRATION/CLOSED/CANCELLED (Sprint T7 + 24A) are unchanged, so no existing
--    row, query, or API contract breaks. OPEN is the "Published" step; see
--    PerformanceCycleLifecyclePolicy for the full transition graph and the rationale for keeping
--    the original code names.
--
-- 2) performance_cycle_transitions: append-only audit log, one row per successful cycle status
--    transition (who / when / from / to / notes). Mirrors the audit-trail convention already used
--    for appraisal_ratings (append-only history rows alongside the mutable aggregate).
--
-- 3) appraisal_cycle_launch_batches: one row per bulk-launch request. Tracks the resolved filter
--    criteria, run status, and per-outcome counters so a launch of thousands of employees has a
--    durable, queryable audit trail and progress indicator instead of a synchronous fire-and-forget
--    HTTP call.
--
-- 4) appraisals.launch_batch_id: nullable back-reference so every appraisal created by a bulk
--    launch traces to the batch that created it, without a separate (and, at 100k+ employees,
--    very large) per-item table — the appraisals table itself is the per-item audit record.

ALTER TABLE performance_cycles DROP CONSTRAINT ck_perf_cycles_status;
ALTER TABLE performance_cycles ADD CONSTRAINT ck_perf_cycles_status
    CHECK (status IN ('DRAFT','OPEN','SELF_REVIEW','MANAGER_REVIEW','REVIEWER_REVIEW',
                      'CALIBRATION','HR_REVIEW','FINAL_APPROVAL','RELEASED','CLOSED','CANCELLED'));

CREATE TABLE performance_cycle_transitions (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    cycle_id              UUID        NOT NULL REFERENCES performance_cycles (id),
    from_status           VARCHAR(32) NOT NULL,
    to_status             VARCHAR(32) NOT NULL,
    notes                 VARCHAR(2000),
    transitioned_by       UUID,
    transitioned_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_perf_cycle_transitions_cycle
    ON performance_cycle_transitions (tenant_id, cycle_id, transitioned_at);

CREATE TABLE appraisal_cycle_launch_batches (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    cycle_id                 UUID        NOT NULL REFERENCES performance_cycles (id),
    template_id              UUID        NOT NULL REFERENCES appraisal_templates (id),
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    filter_org_unit_ids      VARCHAR(8000),
    filter_include_descendants BOOLEAN   NOT NULL DEFAULT TRUE,
    filter_employment_type_id UUID,
    filter_employee_status   VARCHAR(32),
    total_matched            INT         NOT NULL DEFAULT 0,
    total_created             INT         NOT NULL DEFAULT 0,
    total_skipped_existing    INT         NOT NULL DEFAULT 0,
    total_failed              INT         NOT NULL DEFAULT 0,
    error_message             VARCHAR(4000),
    started_at                TIMESTAMPTZ,
    completed_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_launch_batches_status
        CHECK (status IN ('PENDING','RUNNING','COMPLETED','PARTIALLY_COMPLETED','FAILED'))
);

CREATE INDEX ix_launch_batches_cycle
    ON appraisal_cycle_launch_batches (tenant_id, cycle_id, created_at)
    WHERE deleted_at IS NULL;

-- Prevents two launches from racing for the same cycle: at most one PENDING/RUNNING batch per
-- cycle at a time. A batch that fails or completes stops matching this partial index, freeing the
-- cycle for another launch.
CREATE UNIQUE INDEX ux_launch_batches_cycle_active
    ON appraisal_cycle_launch_batches (tenant_id, cycle_id)
    WHERE deleted_at IS NULL AND status IN ('PENDING','RUNNING');

ALTER TABLE appraisals ADD COLUMN launch_batch_id UUID REFERENCES appraisal_cycle_launch_batches (id);

CREATE INDEX ix_appraisals_launch_batch
    ON appraisals (launch_batch_id)
    WHERE launch_batch_id IS NOT NULL AND deleted_at IS NULL;
