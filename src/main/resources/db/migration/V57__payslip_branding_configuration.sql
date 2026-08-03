-- Sprint 24K item 3 — Payslip PDF generation. Employer branding is stored as text/metadata only
-- (no raster logo bytes) consistent with this codebase's document-metadata+storageUri convention;
-- a logo reference is kept as a storage URI for a future renderer to resolve, never fetched or
-- embedded by the PDF generator itself today.

CREATE TABLE payslip_branding_configurations (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    display_name      VARCHAR(255) NOT NULL,
    address_line1     VARCHAR(255),
    address_line2     VARCHAR(255),
    support_email     VARCHAR(255),
    footer_note       VARCHAR(1000),
    logo_storage_uri  VARCHAR(2000),
    password_policy   VARCHAR(32) NOT NULL DEFAULT 'NONE',
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payslip_branding_password_policy
        CHECK (password_policy IN ('NONE', 'EMPLOYEE_NUMBER', 'DATE_OF_BIRTH_DDMMYYYY')),
    CONSTRAINT uq_payslip_branding_company UNIQUE (tenant_id, company_id)
);
