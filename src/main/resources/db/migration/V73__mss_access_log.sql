-- Sprint 27A — ESS/MSS foundation, item 8.
--
-- Cross-employee access log (PRD §14, audit finding 3.3/3.6): an immutable record of every time
-- one employee's data is read or acted on by someone else (a manager viewing/acting on a direct
-- report's record, in future sub-sprints' MSS reads) — WHO (actor), on WHOM (target), WHAT
-- (action), whether it was GRANTED or denied, and WHEN. Insert-only: rows are never updated, only
-- purged on a retention schedule (see PurgeJob.purgeMssAccessLogs). No consuming call site exists
-- yet in this sprint — this table and the service that writes to it are foundation only, ready for
-- 27C's "My Team" drill-down and the later MSS parity extensions to call.
CREATE TABLE mss_access_log (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    actor_employee_id   UUID        NOT NULL,
    target_employee_id  UUID        NOT NULL,
    action              VARCHAR(100) NOT NULL,
    granted             BOOLEAN     NOT NULL,
    reason              VARCHAR(500),
    occurred_at         TIMESTAMPTZ NOT NULL,
    ip_address          VARCHAR(64),
    correlation_id      VARCHAR(100)
);

-- The query this table exists to answer: "who looked at this employee's data, and when" —
-- and its mirror, "what has this manager looked at."
CREATE INDEX ix_mss_access_log_tenant_target ON mss_access_log (tenant_id, target_employee_id, occurred_at);
CREATE INDEX ix_mss_access_log_tenant_actor ON mss_access_log (tenant_id, actor_employee_id, occurred_at);

-- Retention purge (see PurgeJob) sweeps by age alone.
CREATE INDEX ix_mss_access_log_occurred_at ON mss_access_log (occurred_at);
