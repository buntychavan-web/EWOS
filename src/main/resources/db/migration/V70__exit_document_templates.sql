-- Sprint 26 — Exit Management V1, increment 6.
--
-- Configurable exit letter generation: a company- (and optionally org-unit-) scoped template per
-- document type, rendered to PDF on demand (never persisted as a blob — same "regenerate per
-- download" approach as payslip PDFs). Adds the two document types Sprint 26 asks for that didn't
-- exist yet (ACCEPTANCE_LETTER, SERVICE_CERTIFICATE); RELIEVING_LETTER, EXPERIENCE_LETTER,
-- FNF_STATEMENT, PF_STATEMENT, and OTHER already existed.
ALTER TABLE exit_documents DROP CONSTRAINT ck_exit_doc_type;
ALTER TABLE exit_documents ADD CONSTRAINT ck_exit_doc_type
    CHECK (document_type IN
        ('ACCEPTANCE_LETTER','RELIEVING_LETTER','EXPERIENCE_LETTER','SERVICE_CERTIFICATE',
         'FNF_STATEMENT','PF_STATEMENT','OTHER'));

CREATE TABLE exit_document_templates (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    company_id    UUID        NOT NULL,
    org_unit_id   UUID,
    document_type VARCHAR(32) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body_template VARCHAR(8000) NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version_no    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_exit_doc_template_type
        CHECK (document_type IN
            ('ACCEPTANCE_LETTER','RELIEVING_LETTER','EXPERIENCE_LETTER','SERVICE_CERTIFICATE',
             'FNF_STATEMENT','PF_STATEMENT','OTHER'))
);

CREATE INDEX idx_exit_document_templates_lookup
    ON exit_document_templates (tenant_id, company_id, org_unit_id, document_type)
    WHERE deleted_at IS NULL;
