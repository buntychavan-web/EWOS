-- Sprint 14.3 — Data Exchange operational framework.
--
-- Tracks the intent/queue of exchanging payroll & HR business data with an external system
-- (a future HRMS connector, not built here — see EWOS_MASTER_ARCHITECTURE "External HRMS
-- Connectors", explicitly out of scope for 14.3). Records are created automatically by
-- listeners on the platform's existing domain events (PayrollEvent, WorkflowEvent) and/or
-- explicitly via the REST API; nothing here performs an actual outbound call.
--
-- Lifecycle: PENDING -> PROCESSING -> SUCCESS -> ACKNOWLEDGED
--                            \-> FAILED -> RETRY -> PROCESSING (loop)
--            (PENDING/PROCESSING/FAILED/RETRY) -> CANCELLED
--
-- data_exchange_history mirrors workflow_history's append-only audit pattern (V11).

CREATE TABLE data_exchange_records (
    id                 UUID        PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    company_id         UUID        NOT NULL,
    exchange_type      VARCHAR(64) NOT NULL,
    source_event_type  VARCHAR(128),
    correlation_id     VARCHAR(256) NOT NULL,
    payload_json       VARCHAR(4000),
    status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count        INTEGER     NOT NULL DEFAULT 0,
    next_retry_at      TIMESTAMPTZ,
    acknowledged_at    TIMESTAMPTZ,
    acknowledged_by    UUID,
    error_code         VARCHAR(64),
    error_message      VARCHAR(2048),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID,
    deleted_at         TIMESTAMPTZ,
    version_no         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_dx_records_status
        CHECK (status IN ('PENDING','PROCESSING','SUCCESS','FAILED','RETRY','ACKNOWLEDGED','CANCELLED')),
    CONSTRAINT ck_dx_records_retry_count_nonneg CHECK (retry_count >= 0),
    CONSTRAINT ck_dx_records_ack_has_ts
        CHECK ((status = 'ACKNOWLEDGED') = (acknowledged_at IS NOT NULL))
);

CREATE INDEX ix_dx_records_tenant_company_status
    ON data_exchange_records (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_dx_records_correlation
    ON data_exchange_records (tenant_id, correlation_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_dx_records_next_retry
    ON data_exchange_records (next_retry_at)
    WHERE deleted_at IS NULL AND status = 'RETRY';

CREATE TABLE data_exchange_history (
    id             UUID        PRIMARY KEY,
    record_id      UUID        NOT NULL REFERENCES data_exchange_records (id) ON DELETE CASCADE,
    from_status    VARCHAR(32),
    to_status      VARCHAR(32) NOT NULL,
    actor_id       UUID,
    notes          VARCHAR(2048),
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_dx_history_record_time
    ON data_exchange_history (record_id, occurred_at DESC);

INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'DATA_EXCHANGE_READ',  'Read data exchange records and history'),
    (gen_random_uuid(), 'DATA_EXCHANGE_WRITE', 'Create, retry, acknowledge, and cancel data exchange records');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('DATA_EXCHANGE_READ','DATA_EXCHANGE_WRITE')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
