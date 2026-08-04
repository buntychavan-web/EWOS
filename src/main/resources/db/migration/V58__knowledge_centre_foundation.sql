-- Sprint 24K item 7 — Knowledge Centre backend foundation (not the Knowledge Centre itself).
-- Stores metadata + a storage URI for statutory source documents (Income Tax Act sections, CBDT
-- circulars, EPFO/ESIC/PT/LWF notifications) and company payroll policies, with version history and
-- effective dates so a future retrieval feature (rule-based today, potentially AI-assisted later —
-- see docs/architecture/knowledge-centre-foundation.md) always has a dated, versioned source of
-- truth to cite. Document *content* is never stored inline — storage_uri only, matching this
-- codebase's existing document-metadata pattern (CandidateDocument, ExitDocument,
-- TaxDeclarationProof).

CREATE TABLE knowledge_documents (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID,
    document_family_id  UUID        NOT NULL,
    version_number       INTEGER     NOT NULL DEFAULT 1,
    source_type         VARCHAR(32) NOT NULL,
    title               VARCHAR(500) NOT NULL,
    reference_number    VARCHAR(255),
    summary             VARCHAR(4000),
    tags                VARCHAR(1000),
    storage_uri         VARCHAR(2000),
    effective_from      DATE        NOT NULL,
    effective_to        DATE,
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    deleted_at          TIMESTAMPTZ,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_kd_source_type CHECK (source_type IN (
        'INCOME_TAX_ACT', 'CBDT_CIRCULAR', 'EPFO_CIRCULAR', 'ESIC_CIRCULAR',
        'PT_NOTIFICATION', 'LWF_NOTIFICATION', 'COMPANY_POLICY', 'OTHER')),
    CONSTRAINT ck_kd_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'SUPERSEDED', 'ARCHIVED')),
    CONSTRAINT ck_kd_version_positive CHECK (version_number > 0)
);

CREATE INDEX ix_knowledge_documents_family ON knowledge_documents (document_family_id, version_number DESC);
CREATE INDEX ix_knowledge_documents_effective
    ON knowledge_documents (tenant_id, source_type, status, effective_from);
CREATE INDEX ix_knowledge_documents_company ON knowledge_documents (tenant_id, company_id);
