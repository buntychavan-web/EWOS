-- Sprint 2 — Reimbursement Backend Foundation. Four tables:
--   reimbursement_categories       tenant-scoped configurable master data, mirrors leave_types
--                                   exactly (see LeaveType's own javadoc: "kept as data, not enum,
--                                   so tenants can add jurisdiction-specific categories without a
--                                   schema change").
--   reimbursement_claims           the claim itself; status is the authoritative lifecycle state,
--                                   independent of any optional workflow instance (see Resignation's
--                                   attachApprovalWorkflow pattern).
--   reimbursement_claim_attachments  metadata-only receipt records, mirrors candidate_documents.
--   reimbursement_claim_history    append-only action log, mirrors workflow_history's shape and its
--                                   "never soft-deleted, never versioned" convention — guaranteed to
--                                   exist regardless of whether a tenant has configured a workflow.

CREATE TABLE reimbursement_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order  INTEGER NOT NULL DEFAULT 100,
    deleted_at  TIMESTAMPTZ,
    version_no  BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID
);

CREATE UNIQUE INDEX ux_reimbursement_categories_tenant_code
    ON reimbursement_categories (tenant_id, code)
    WHERE deleted_at IS NULL;

CREATE TABLE reimbursement_claims (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    company_id            UUID NOT NULL,
    employee_id           UUID NOT NULL REFERENCES employees (id),
    claim_number          VARCHAR(32) NOT NULL,
    category_id           UUID NOT NULL REFERENCES reimbursement_categories (id),
    amount                NUMERIC(18, 4) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    claim_date            DATE NOT NULL,
    description           VARCHAR(2000) NOT NULL,
    status                VARCHAR(24) NOT NULL,
    data_source           VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    submitted_at          TIMESTAMPTZ,
    manager_decision_at   TIMESTAMPTZ,
    manager_decision_by   UUID,
    finance_decision_at   TIMESTAMPTZ,
    finance_decision_by   UUID,
    rejection_reason      VARCHAR(2000),
    workflow_instance_id  UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    CONSTRAINT ck_reimbursement_claims_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_reimbursement_claims_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'MANAGER_APPROVED', 'FINANCE_APPROVED',
        'MANAGER_REJECTED', 'FINANCE_REJECTED', 'CANCELLED')),
    CONSTRAINT ck_reimbursement_claims_data_source CHECK (data_source IN ('MANUAL', 'OCR_ASSISTED'))
);

CREATE UNIQUE INDEX ux_reimbursement_claims_tenant_number
    ON reimbursement_claims (tenant_id, claim_number);

CREATE INDEX ix_reimbursement_claims_employee_status
    ON reimbursement_claims (tenant_id, employee_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_reimbursement_claims_manager_status
    ON reimbursement_claims (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE reimbursement_claim_attachments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL,
    claim_id       UUID NOT NULL REFERENCES reimbursement_claims (id),
    filename       VARCHAR(512) NOT NULL,
    mime_type      VARCHAR(128) NOT NULL,
    size_bytes     BIGINT NOT NULL,
    storage_uri    VARCHAR(1024) NOT NULL,
    notes          VARCHAR(2000),
    ocr_assisted   BOOLEAN NOT NULL DEFAULT FALSE,
    ocr_raw_response TEXT,
    uploaded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ,
    version_no     BIGINT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_reimbursement_claim_attachments_claim
    ON reimbursement_claim_attachments (claim_id)
    WHERE deleted_at IS NULL;

-- Append-only. No deleted_at, no version_no — mirrors workflow_history exactly.
CREATE TABLE reimbursement_claim_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id    UUID NOT NULL REFERENCES reimbursement_claims (id),
    actor_id    UUID,
    action      VARCHAR(24) NOT NULL,
    from_status VARCHAR(24),
    to_status   VARCHAR(24) NOT NULL,
    notes       VARCHAR(2000),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reimbursement_claim_history_action CHECK (action IN (
        'CREATE_DRAFT', 'SUBMIT', 'MANAGER_APPROVE', 'MANAGER_REJECT',
        'FINANCE_APPROVE', 'FINANCE_REJECT', 'CANCEL'))
);

CREATE INDEX ix_reimbursement_claim_history_claim
    ON reimbursement_claim_history (claim_id, occurred_at);

-- Default category seed, per tenant — deliberately generic; Finance/Product may rename/add later
-- via a future admin surface (not built in Sprint 2, see PR description).
INSERT INTO reimbursement_categories (id, tenant_id, code, name, sort_order)
SELECT gen_random_uuid(), t.id, c.code, c.name, c.sort_order
FROM tenants t
CROSS JOIN (VALUES
    ('TRAVEL', 'Travel', 10),
    ('ACCOMMODATION', 'Accommodation', 20),
    ('MEALS', 'Meals', 30),
    ('COMMUNICATION', 'Communication', 40),
    ('OFFICE_SUPPLIES', 'Office Supplies', 50),
    ('TRAINING', 'Training', 60),
    ('CLIENT_ENTERTAINMENT', 'Client Entertainment', 70),
    ('OTHER', 'Other', 100)
) AS c(code, name, sort_order);

-- Permissions — seeded and granted only to SYSTEM_ADMIN, following every prior module's exact
-- pattern (see V13__leave_engine.sql, V62__payroll_maker_checker.sql). Tenant-specific roles for
-- ordinary employees/managers/finance staff are provisioned per-tenant outside this migration.
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'REIMBURSEMENT_READ', 'View own reimbursement claims'),
    (gen_random_uuid(), 'REIMBURSEMENT_WRITE', 'Create, edit, submit, and cancel own reimbursement claims'),
    (gen_random_uuid(), 'REIMBURSEMENT_FINANCE_APPROVE', 'Decide (approve/reject) a reimbursement claim at finance level'),
    (gen_random_uuid(), 'REIMBURSEMENT_ADMIN', 'Manage reimbursement categories')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'REIMBURSEMENT_READ', 'REIMBURSEMENT_WRITE', 'REIMBURSEMENT_FINANCE_APPROVE', 'REIMBURSEMENT_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
