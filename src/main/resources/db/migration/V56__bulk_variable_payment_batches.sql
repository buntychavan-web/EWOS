-- Sprint 24K item 2 — Bulk Variable Input (Bonus/Incentives/Variable Pay/Arrears/Adjustments/
-- One-time Payments). Each committed batch creates one or more payroll_arrears rows (the existing
-- one-time-payment vehicle already consumed by PayrollCalculator/PayrollRunService) plus one header
-- row here recording the batch itself for audit — who uploaded it, how many rows, how many
-- succeeded/failed, and its outcome. A batch is only ever committed atomically: if any row fails
-- validation, nothing in the batch is written (see BulkVariablePaymentService), so there is no
-- partial-batch state to represent here.

CREATE TABLE bulk_variable_payment_batches (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    company_id     UUID        NOT NULL,
    source_filename VARCHAR(255),
    status         VARCHAR(16) NOT NULL,
    total_rows     INTEGER     NOT NULL DEFAULT 0,
    valid_rows     INTEGER     NOT NULL DEFAULT 0,
    error_rows     INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    version_no     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_bulk_var_payment_status CHECK (status IN ('COMMITTED', 'REJECTED'))
);

CREATE INDEX ix_bulk_var_payment_batches_company
    ON bulk_variable_payment_batches (tenant_id, company_id, created_at DESC);

-- Links each arrear created by a bulk upload back to its batch, for audit traceability. Nullable —
-- arrears created through the existing single-row API have no batch.
ALTER TABLE payroll_arrears ADD COLUMN bulk_upload_batch_id UUID
    REFERENCES bulk_variable_payment_batches (id);

CREATE INDEX ix_payroll_arrears_bulk_batch ON payroll_arrears (bulk_upload_batch_id);
