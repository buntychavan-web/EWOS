-- Sprint 14.4 — Generic Integration Adapter Framework, Business Error Classification,
-- and Client Go-Live Configuration.
--
-- Zero changes to Sprint 14.3's data_exchange_records/data_exchange_history — this module
-- composes entirely against DataExchangeService's existing public API
-- (getById/startProcessing/markSuccess/markFailed). Error classification is attempt-scoped
-- (one execution can fail, be classified, and a later retry can still succeed), so it lives on
-- integration_execution_records rather than on the exchange record itself.

-- =====================================================================
-- integration_configurations — which adapter + connection settings a company uses per
-- exchange type (e.g. PAYROLL_RUN_EXPORT -> SFTP drop to the client's inbound folder).
-- =====================================================================
CREATE TABLE integration_configurations (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    company_id      UUID        NOT NULL,
    exchange_type   VARCHAR(64) NOT NULL,
    adapter_type    VARCHAR(32) NOT NULL,
    config_json     VARCHAR(4000) NOT NULL,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_integration_configurations_adapter_type
        CHECK (adapter_type IN ('REST','SFTP','CSV','EXCEL','FILE_UPLOAD'))
);

CREATE UNIQUE INDEX ux_integration_configurations_company_exchange_active
    ON integration_configurations (tenant_id, company_id, LOWER(exchange_type))
    WHERE deleted_at IS NULL AND active = TRUE;

CREATE INDEX ix_integration_configurations_company
    ON integration_configurations (tenant_id, company_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- integration_execution_records — append-only, one row per adapter execution attempt.
-- Doubles as the enhanced operational audit trail for the integration layer (same
-- append-only convention as workflow_history / data_exchange_history).
-- =====================================================================
CREATE TABLE integration_execution_records (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    data_exchange_record_id  UUID        NOT NULL REFERENCES data_exchange_records (id) ON DELETE CASCADE,
    configuration_id         UUID        REFERENCES integration_configurations (id),
    -- Nullable: a CONFIGURATION-classified failure (no active configuration found for the
    -- exchange type) never resolved an adapter type in the first place.
    adapter_type             VARCHAR(32),
    attempt_number           INTEGER     NOT NULL DEFAULT 1,
    outcome                  VARCHAR(16) NOT NULL,
    error_classification     VARCHAR(32),
    error_message            VARCHAR(2048),
    started_at               TIMESTAMPTZ NOT NULL,
    completed_at             TIMESTAMPTZ NOT NULL,
    duration_ms              BIGINT,
    actor_id                 UUID,
    CONSTRAINT ck_integration_execution_records_outcome
        CHECK (outcome IN ('SUCCESS','FAILURE')),
    CONSTRAINT ck_integration_execution_records_error_classification
        CHECK (error_classification IS NULL OR error_classification IN
            ('VALIDATION','AUTHENTICATION','TRANSIENT_NETWORK','DATA_MAPPING','EXTERNAL_SYSTEM','CONFIGURATION','UNKNOWN')),
    CONSTRAINT ck_integration_execution_records_failure_has_classification
        CHECK (outcome = 'SUCCESS' OR error_classification IS NOT NULL)
);

CREATE INDEX ix_integration_execution_records_record
    ON integration_execution_records (data_exchange_record_id, started_at DESC);

CREATE INDEX ix_integration_execution_records_company_outcome
    ON integration_execution_records (tenant_id, company_id, outcome, started_at DESC);

-- =====================================================================
-- client_go_live_configurations — one row per Company, tracking outsourcing go-live readiness.
-- =====================================================================
CREATE TABLE client_go_live_configurations (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    client_id       UUID        NOT NULL REFERENCES clients (id),
    company_id      UUID        NOT NULL REFERENCES companies (id),
    go_live_date    DATE,
    status          VARCHAR(32) NOT NULL DEFAULT 'PLANNING',
    notes           VARCHAR(2048),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_client_go_live_configurations_status
        CHECK (status IN ('PLANNING','READY','LIVE','SUSPENDED'))
);

CREATE UNIQUE INDEX ux_client_go_live_configurations_company_alive
    ON client_go_live_configurations (company_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_client_go_live_configurations_client
    ON client_go_live_configurations (tenant_id, client_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'INTEGRATION_READ',  'Read integration configurations, executions, and monitoring data'),
    (gen_random_uuid(), 'INTEGRATION_WRITE', 'Author integration configurations and trigger executions'),
    (gen_random_uuid(), 'GOLIVE_READ',       'Read client go-live configurations'),
    (gen_random_uuid(), 'GOLIVE_WRITE',      'Author client go-live configurations');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('INTEGRATION_READ','INTEGRATION_WRITE','GOLIVE_READ','GOLIVE_WRITE')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
