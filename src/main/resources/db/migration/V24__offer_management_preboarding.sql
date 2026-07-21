-- Offer Management & Pre-Boarding (T4 — Volume 5)
--
-- Layered on the T2 ATS and T3 Interview surfaces. Introduces:
--
--   Offer Management
--     * offer_templates       — reusable per-tenant offer-letter templates
--     * offers                — versioned offer records tied to a job application
--     * offer_negotiations    — round of counter-proposal / negotiation history
--
--   Pre-Boarding
--     * preboarding_task_templates — reusable per-tenant catalogue of pre-joining tasks
--     * preboarding_checklists     — one per accepted offer, tracks joining fulfilment
--     * preboarding_task_instances — the actual tasks on a checklist
--
-- Offers are versioned in place: revisions bump `version` and point back at
-- `previous_offer_id`. Digital acceptance captures a signature string + timestamp on the row
-- itself; the offer letter PDF lives external and is referenced by URI. Pre-boarding runs on top
-- of the accepted offer and produces the final joining confirmation that the Employee module
-- consumes to create the employee record.

-- =====================================================================
-- Offer templates
-- =====================================================================
CREATE TABLE offer_templates (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    company_id     UUID        NOT NULL,
    code           VARCHAR(64) NOT NULL,
    name           VARCHAR(256) NOT NULL,
    description    VARCHAR(2000),
    body_template  TEXT        NOT NULL,
    default_currency VARCHAR(3),
    default_notice_period_days INT,
    default_probation_days     INT,
    default_expiry_days        INT NOT NULL DEFAULT 7,
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version_no     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_offer_templates_currency
        CHECK (default_currency IS NULL OR LENGTH(default_currency) = 3),
    CONSTRAINT ck_offer_templates_positive
        CHECK ((default_notice_period_days IS NULL OR default_notice_period_days >= 0)
           AND (default_probation_days     IS NULL OR default_probation_days     >= 0)
           AND default_expiry_days > 0)
);

CREATE UNIQUE INDEX ux_offer_templates_company_code_alive
    ON offer_templates (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Offers (versioned)
-- =====================================================================
CREATE TABLE offers (
    id                        UUID        PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    company_id                UUID        NOT NULL,
    offer_number              VARCHAR(64) NOT NULL,
    application_id            UUID        NOT NULL REFERENCES job_applications (id),
    candidate_id              UUID        NOT NULL REFERENCES candidates (id),
    job_requisition_id        UUID        NOT NULL REFERENCES job_requisitions (id),
    template_id               UUID        REFERENCES offer_templates (id),
    version                   INT         NOT NULL DEFAULT 1,
    previous_offer_id         UUID        REFERENCES offers (id),
    designation               VARCHAR(256) NOT NULL,
    department_org_unit_id    UUID        REFERENCES organization_units (id),
    location                  VARCHAR(256),
    employment_type           VARCHAR(32) NOT NULL,
    target_joining_date       DATE,
    currency                  VARCHAR(3)  NOT NULL,
    base_salary               NUMERIC(18,2) NOT NULL,
    variable_pay              NUMERIC(18,2),
    one_time_bonus            NUMERIC(18,2),
    hiring_bonus              NUMERIC(18,2),
    retention_bonus           NUMERIC(18,2),
    total_ctc                 NUMERIC(18,2) NOT NULL,
    salary_breakdown_json     TEXT,
    benefits_json             TEXT,
    notice_period_days        INT,
    probation_days            INT,
    offer_body                TEXT,
    offer_document_uri        VARCHAR(1024),
    approval_workflow_instance_id UUID,
    status                    VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    submitted_at              TIMESTAMPTZ,
    approved_at               TIMESTAMPTZ,
    approved_by               UUID,
    approval_notes            VARCHAR(4000),
    extended_at               TIMESTAMPTZ,
    expires_at                TIMESTAMPTZ,
    accepted_at               TIMESTAMPTZ,
    candidate_signature       VARCHAR(1024),
    candidate_signed_at       TIMESTAMPTZ,
    declined_at               TIMESTAMPTZ,
    decline_reason            VARCHAR(4000),
    revised_at                TIMESTAMPTZ,
    withdrawn_at              TIMESTAMPTZ,
    withdrawn_reason          VARCHAR(4000),
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                UUID,
    updated_by                UUID,
    deleted_at                TIMESTAMPTZ,
    version_no                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_offers_status
        CHECK (status IN (
            'DRAFT','PENDING_APPROVAL','APPROVED','REJECTED',
            'EXTENDED','ACCEPTED','DECLINED','REVISED','EXPIRED','WITHDRAWN')),
    CONSTRAINT ck_offers_employment_type
        CHECK (employment_type IN (
            'FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERN','CONSULTANT')),
    CONSTRAINT ck_offers_currency
        CHECK (LENGTH(currency) = 3),
    CONSTRAINT ck_offers_version
        CHECK (version >= 1),
    CONSTRAINT ck_offers_amounts_non_negative
        CHECK (base_salary >= 0
           AND total_ctc >= 0
           AND (variable_pay   IS NULL OR variable_pay   >= 0)
           AND (one_time_bonus IS NULL OR one_time_bonus >= 0)
           AND (hiring_bonus   IS NULL OR hiring_bonus   >= 0)
           AND (retention_bonus IS NULL OR retention_bonus >= 0)),
    CONSTRAINT ck_offers_notice_probation
        CHECK ((notice_period_days IS NULL OR notice_period_days >= 0)
           AND (probation_days     IS NULL OR probation_days     >= 0))
);

CREATE UNIQUE INDEX ux_offers_number_alive
    ON offers (tenant_id, company_id, LOWER(offer_number))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_offers_application
    ON offers (tenant_id, application_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_offers_candidate
    ON offers (tenant_id, candidate_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_offers_status
    ON offers (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_offers_expires
    ON offers (tenant_id, expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL AND status = 'EXTENDED';

-- =====================================================================
-- Offer negotiations
-- =====================================================================
CREATE TABLE offer_negotiations (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    offer_id          UUID        NOT NULL REFERENCES offers (id),
    proposed_by       VARCHAR(16) NOT NULL,
    proposed_changes_json TEXT,
    notes             VARCHAR(4000),
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at      TIMESTAMPTZ,
    accepted          BOOLEAN,
    resulting_offer_id UUID       REFERENCES offers (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_offer_negotiations_proposed_by
        CHECK (proposed_by IN ('CANDIDATE','COMPANY'))
);

CREATE INDEX ix_offer_negotiations_offer
    ON offer_negotiations (tenant_id, offer_id, submitted_at DESC);

-- =====================================================================
-- Pre-boarding task templates
-- =====================================================================
CREATE TABLE preboarding_task_templates (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    code              VARCHAR(64) NOT NULL,
    name              VARCHAR(256) NOT NULL,
    description       VARCHAR(2000),
    task_type         VARCHAR(32) NOT NULL,
    sort_order        INT         NOT NULL DEFAULT 0,
    mandatory         BOOLEAN     NOT NULL DEFAULT TRUE,
    default_owner     VARCHAR(16) NOT NULL DEFAULT 'HR',
    default_sla_days  INT,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_preboarding_task_type
        CHECK (task_type IN (
            'DOCUMENT_COLLECTION','ID_VERIFICATION','BACKGROUND_VERIFICATION','MEDICAL_CHECK',
            'IT_ASSET','EMAIL_ACCOUNT','EMPLOYEE_ID','JOINING_KIT','JOINING_CONFIRMATION','OTHER')),
    CONSTRAINT ck_preboarding_task_owner
        CHECK (default_owner IN ('HR','IT','ADMIN','HIRING_MANAGER','CANDIDATE','RECRUITER','VENDOR'))
);

CREATE UNIQUE INDEX ux_preboarding_task_templates_code_alive
    ON preboarding_task_templates (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_preboarding_task_templates_active
    ON preboarding_task_templates (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Pre-boarding checklists
-- =====================================================================
CREATE TABLE preboarding_checklists (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    offer_id          UUID        NOT NULL REFERENCES offers (id),
    application_id    UUID        NOT NULL REFERENCES job_applications (id),
    candidate_id      UUID        NOT NULL REFERENCES candidates (id),
    joining_date      DATE,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    completion_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    joining_confirmed_at TIMESTAMPTZ,
    joining_confirmed_by UUID,
    employee_id       UUID        REFERENCES employees (id),
    notes             VARCHAR(4000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_preboarding_checklists_status
        CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','CANCELLED','JOINED','NO_SHOW')),
    CONSTRAINT ck_preboarding_checklists_completion
        CHECK (completion_percent >= 0 AND completion_percent <= 100)
);

CREATE UNIQUE INDEX ux_preboarding_checklists_offer_alive
    ON preboarding_checklists (tenant_id, offer_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_preboarding_checklists_status
    ON preboarding_checklists (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_preboarding_checklists_joining
    ON preboarding_checklists (tenant_id, company_id, joining_date)
    WHERE deleted_at IS NULL AND joining_date IS NOT NULL;

-- =====================================================================
-- Pre-boarding task instances
-- =====================================================================
CREATE TABLE preboarding_task_instances (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    checklist_id      UUID        NOT NULL REFERENCES preboarding_checklists (id),
    template_id       UUID        REFERENCES preboarding_task_templates (id),
    name              VARCHAR(256) NOT NULL,
    task_type         VARCHAR(32) NOT NULL,
    owner             VARCHAR(16) NOT NULL DEFAULT 'HR',
    assigned_employee_id UUID     REFERENCES employees (id),
    mandatory         BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order        INT         NOT NULL DEFAULT 0,
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    due_date          DATE,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    completed_by      UUID,
    external_ref      VARCHAR(512),
    result_json       TEXT,
    notes             VARCHAR(4000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_preboarding_task_instances_task_type
        CHECK (task_type IN (
            'DOCUMENT_COLLECTION','ID_VERIFICATION','BACKGROUND_VERIFICATION','MEDICAL_CHECK',
            'IT_ASSET','EMAIL_ACCOUNT','EMPLOYEE_ID','JOINING_KIT','JOINING_CONFIRMATION','OTHER')),
    CONSTRAINT ck_preboarding_task_instances_owner
        CHECK (owner IN ('HR','IT','ADMIN','HIRING_MANAGER','CANDIDATE','RECRUITER','VENDOR')),
    CONSTRAINT ck_preboarding_task_instances_status
        CHECK (status IN (
            'PENDING','IN_PROGRESS','WAITING_ON_CANDIDATE','WAITING_ON_COMPANY',
            'COMPLETED','SKIPPED','FAILED'))
);

CREATE INDEX ix_preboarding_task_instances_checklist
    ON preboarding_task_instances (tenant_id, checklist_id, sort_order)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_preboarding_task_instances_status
    ON preboarding_task_instances (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_preboarding_task_instances_due
    ON preboarding_task_instances (tenant_id, due_date)
    WHERE deleted_at IS NULL AND due_date IS NOT NULL AND status NOT IN ('COMPLETED','SKIPPED');

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'OFFER_READ',         'View offers and offer templates'),
    (gen_random_uuid(), 'OFFER_WRITE',        'Draft, revise, extend, withdraw offers; log negotiations'),
    (gen_random_uuid(), 'OFFER_APPROVE',      'Approve or reject offers pending approval'),
    (gen_random_uuid(), 'OFFER_ACCEPT',       'Record candidate acceptance / decline'),
    (gen_random_uuid(), 'OFFER_ADMIN',        'Administer offer templates; hard-delete offer data'),
    (gen_random_uuid(), 'PREBOARDING_READ',   'View pre-boarding checklists and task instances'),
    (gen_random_uuid(), 'PREBOARDING_WRITE',  'Manage pre-boarding tasks: assign, update status, upload results'),
    (gen_random_uuid(), 'PREBOARDING_ADMIN',  'Administer pre-boarding templates and configuration');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'OFFER_READ','OFFER_WRITE','OFFER_APPROVE','OFFER_ACCEPT','OFFER_ADMIN',
    'PREBOARDING_READ','PREBOARDING_WRITE','PREBOARDING_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
