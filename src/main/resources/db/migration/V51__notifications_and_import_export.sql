-- Sprint 24E — PMS Version 1 closure: notification templates/email log, and a shared
-- import-job audit trail used by the new Goal/Competency/Development-Plan import endpoints.

CREATE TABLE notification_templates (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID,
    type           VARCHAR(32) NOT NULL,
    title_template VARCHAR(256) NOT NULL,
    body_template  VARCHAR(2048),
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

-- Partial unique indexes: at most one active override per (tenant, type), and at most one
-- active platform-wide default (tenant_id IS NULL) per type.
CREATE UNIQUE INDEX ux_notification_templates_tenant_type
    ON notification_templates (tenant_id, type)
    WHERE active = TRUE AND tenant_id IS NOT NULL;

CREATE UNIQUE INDEX ux_notification_templates_global_type
    ON notification_templates (type)
    WHERE active = TRUE AND tenant_id IS NULL;

CREATE TABLE notification_email_log (
    id               UUID        PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    recipient_actor_id UUID      NOT NULL,
    recipient_email  VARCHAR(255) NOT NULL,
    type             VARCHAR(32) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    error_message    VARCHAR(1000),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX ix_notification_email_log_recipient
    ON notification_email_log (tenant_id, recipient_actor_id, created_at DESC);

-- Shared import-job audit trail (com.ewos.importexport) — one row per bulk-import run,
-- across whichever module triggered it (module is a free-text discriminator, not an FK,
-- exactly like workflow_instances.subject_type).
CREATE TABLE import_jobs (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    company_id     UUID        NOT NULL,
    module         VARCHAR(32) NOT NULL,
    file_name      VARCHAR(256),
    total_rows     INT         NOT NULL,
    success_count  INT         NOT NULL,
    failure_count  INT         NOT NULL,
    status         VARCHAR(16) NOT NULL,
    started_at     TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_import_jobs_tenant_module
    ON import_jobs (tenant_id, module, created_at DESC);

CREATE TABLE import_job_errors (
    id             UUID        PRIMARY KEY,
    import_job_id  UUID        NOT NULL REFERENCES import_jobs (id) ON DELETE CASCADE,
    row_number     INT         NOT NULL,
    row_data       VARCHAR(2000),
    error_message  VARCHAR(1000) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_import_job_errors_job
    ON import_job_errors (import_job_id, row_number);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'NOTIFICATION_TEMPLATE_ADMIN', 'Configure per-tenant notification wording');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'NOTIFICATION_TEMPLATE_ADMIN'
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
