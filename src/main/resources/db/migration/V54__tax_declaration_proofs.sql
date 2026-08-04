-- Sprint 24J — Investment Proof Upload for the Employee Self-Service payroll experience.
--
-- Mirrors candidate_documents / exit_documents exactly: metadata + a storage_uri pointing at
-- wherever the actual file bytes live (uploaded by the client directly to blob storage). This
-- backend never handles raw file bytes for any document type, and proofs follow that same
-- established convention rather than introducing a new one.
CREATE TABLE tax_declaration_proofs (
    id                          UUID        PRIMARY KEY,
    tenant_id                   UUID        NOT NULL,
    company_id                  UUID        NOT NULL,
    employee_tax_declaration_id UUID        NOT NULL REFERENCES employee_tax_declarations (id),
    proof_type                  VARCHAR(32) NOT NULL,
    filename                    VARCHAR(512) NOT NULL,
    mime_type                   VARCHAR(128) NOT NULL,
    size_bytes                  BIGINT      NOT NULL,
    storage_uri                 VARCHAR(1024) NOT NULL,
    notes                       VARCHAR(2000),
    uploaded_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  UUID,
    updated_by                  UUID,
    deleted_at                  TIMESTAMPTZ,
    version_no                  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tdp_proof_type CHECK (proof_type IN (
        'RENT_RECEIPT', 'LIFE_INSURANCE_PREMIUM', 'PPF', 'ELSS', 'HOME_LOAN_INTEREST',
        'HEALTH_INSURANCE_PREMIUM', 'NPS_CONTRIBUTION', 'TUITION_FEES', 'OTHER'))
);

CREATE INDEX ix_tdp_declaration ON tax_declaration_proofs (employee_tax_declaration_id);
