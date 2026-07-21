-- Applicant Tracking System (T2 — Volume 5)
--
-- Layer on top of T1 Recruitment. Introduces the candidate master, resume + document catalogues,
-- tags, notes, timeline events, communication history, and job applications tying candidates to
-- job requisitions. Application lifecycle drives interviews → offers → onboarding in later
-- Talent milestones (T3–T5).
--
-- Duplicate-candidate detection: partial-unique indexes on LOWER(email) and normalized phone
-- (raw digits only). Candidates whose email/phone matches an existing alive candidate can still
-- be inserted after review — the detector surfaces potential dupes for the workflow to decide.
-- Because different companies within a tenant may share candidate identity, the uniqueness scope
-- is tenant (not tenant+company).
--
-- Timeline events are append-only (no soft delete, no version_no). Everything else is
-- soft-deletable + optimistic-locking as per the rest of the codebase.

-- =====================================================================
-- Candidate master
-- =====================================================================
CREATE TABLE candidates (
    id                         UUID        PRIMARY KEY,
    tenant_id                  UUID        NOT NULL,
    company_id                 UUID        NOT NULL,
    candidate_number           VARCHAR(64) NOT NULL,
    first_name                 VARCHAR(128) NOT NULL,
    middle_name                VARCHAR(128),
    last_name                  VARCHAR(128) NOT NULL,
    email                      VARCHAR(320),
    phone                      VARCHAR(32),
    phone_digits               VARCHAR(32),
    date_of_birth              DATE,
    gender                     VARCHAR(32),
    nationality                VARCHAR(64),
    current_location           VARCHAR(256),
    country                    VARCHAR(64),
    current_employer           VARCHAR(256),
    current_designation        VARCHAR(256),
    total_experience_months    INT,
    current_ctc_currency       VARCHAR(3),
    current_ctc_amount         NUMERIC(18,2),
    expected_ctc_currency      VARCHAR(3),
    expected_ctc_amount        NUMERIC(18,2),
    notice_period_days         INT,
    source                     VARCHAR(32) NOT NULL,
    source_details             VARCHAR(512),
    referrer_employee_id       UUID        REFERENCES employees (id),
    is_internal                BOOLEAN     NOT NULL DEFAULT FALSE,
    internal_employee_id       UUID        REFERENCES employees (id),
    status                     VARCHAR(32) NOT NULL DEFAULT 'NEW',
    linkedin_url               VARCHAR(512),
    github_url                 VARCHAR(512),
    portfolio_url              VARCHAR(512),
    summary                    VARCHAR(4000),
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                 UUID,
    updated_by                 UUID,
    deleted_at                 TIMESTAMPTZ,
    version_no                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidates_status
        CHECK (status IN ('NEW','ACTIVE','ENGAGED','HIRED','ARCHIVED','BLACKLISTED')),
    CONSTRAINT ck_candidates_source
        CHECK (source IN (
            'REFERRAL','JOB_BOARD','AGENCY','CAMPUS','DIRECT','INTERNAL',
            'WEBSITE','LINKEDIN','SOCIAL_MEDIA','EVENT','WALK_IN','OTHER')),
    CONSTRAINT ck_candidates_gender
        CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','NON_BINARY','PREFER_NOT_TO_SAY','OTHER')),
    CONSTRAINT ck_candidates_current_ctc_currency
        CHECK (current_ctc_currency IS NULL OR LENGTH(current_ctc_currency) = 3),
    CONSTRAINT ck_candidates_expected_ctc_currency
        CHECK (expected_ctc_currency IS NULL OR LENGTH(expected_ctc_currency) = 3),
    CONSTRAINT ck_candidates_ctc_non_negative
        CHECK ((current_ctc_amount  IS NULL OR current_ctc_amount  >= 0)
           AND (expected_ctc_amount IS NULL OR expected_ctc_amount >= 0)),
    CONSTRAINT ck_candidates_experience
        CHECK (total_experience_months IS NULL OR total_experience_months >= 0),
    CONSTRAINT ck_candidates_notice
        CHECK (notice_period_days IS NULL OR notice_period_days >= 0),
    CONSTRAINT ck_candidates_internal
        CHECK ((is_internal = FALSE AND internal_employee_id IS NULL) OR (is_internal = TRUE))
);

CREATE UNIQUE INDEX ux_candidates_number_alive
    ON candidates (tenant_id, company_id, LOWER(candidate_number))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_candidates_email_alive
    ON candidates (tenant_id, LOWER(email))
    WHERE deleted_at IS NULL AND email IS NOT NULL;

CREATE INDEX ix_candidates_phone_digits
    ON candidates (tenant_id, phone_digits)
    WHERE deleted_at IS NULL AND phone_digits IS NOT NULL;

CREATE INDEX ix_candidates_status
    ON candidates (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_candidates_source
    ON candidates (tenant_id, company_id, source)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_candidates_name
    ON candidates (tenant_id, LOWER(last_name), LOWER(first_name))
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Candidate resumes
-- =====================================================================
CREATE TABLE candidate_resumes (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    candidate_id      UUID        NOT NULL REFERENCES candidates (id),
    filename          VARCHAR(512) NOT NULL,
    mime_type         VARCHAR(128) NOT NULL,
    size_bytes        BIGINT      NOT NULL,
    storage_uri       VARCHAR(1024) NOT NULL,
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    parsed            BOOLEAN     NOT NULL DEFAULT FALSE,
    parsed_at         TIMESTAMPTZ,
    parser_version    VARCHAR(64),
    raw_text          TEXT,
    structured_json   TEXT,
    uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_resumes_size
        CHECK (size_bytes > 0)
);

CREATE INDEX ix_candidate_resumes_candidate
    ON candidate_resumes (tenant_id, candidate_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_candidate_resumes_primary
    ON candidate_resumes (tenant_id, candidate_id)
    WHERE deleted_at IS NULL AND is_primary = TRUE;

-- =====================================================================
-- Candidate documents
-- =====================================================================
CREATE TABLE candidate_documents (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    candidate_id      UUID        NOT NULL REFERENCES candidates (id),
    document_type     VARCHAR(32) NOT NULL,
    filename          VARCHAR(512) NOT NULL,
    mime_type         VARCHAR(128) NOT NULL,
    size_bytes        BIGINT      NOT NULL,
    storage_uri       VARCHAR(1024) NOT NULL,
    notes             VARCHAR(2000),
    uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_documents_type
        CHECK (document_type IN (
            'ID_PROOF','ADDRESS_PROOF','EDUCATION','EXPERIENCE_LETTER','RELIEVING_LETTER',
            'PAYSLIP','CERTIFICATION','PORTFOLIO','OFFER_LETTER','SIGNED_NDA','OTHER')),
    CONSTRAINT ck_candidate_documents_size
        CHECK (size_bytes > 0)
);

CREATE INDEX ix_candidate_documents_candidate
    ON candidate_documents (tenant_id, candidate_id)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Candidate tags (free-form skill / attribute labels)
-- =====================================================================
CREATE TABLE candidate_tags (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    candidate_id UUID        NOT NULL REFERENCES candidates (id),
    tag          VARCHAR(128) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID
);

CREATE UNIQUE INDEX ux_candidate_tags_unique
    ON candidate_tags (tenant_id, candidate_id, LOWER(tag));

CREATE INDEX ix_candidate_tags_tag
    ON candidate_tags (tenant_id, LOWER(tag));

-- =====================================================================
-- Candidate notes
-- =====================================================================
CREATE TABLE candidate_notes (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    candidate_id UUID        NOT NULL REFERENCES candidates (id),
    note_type    VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    body         VARCHAR(8000) NOT NULL,
    is_private   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by   UUID,
    updated_by   UUID,
    deleted_at   TIMESTAMPTZ,
    version_no   BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_notes_type
        CHECK (note_type IN ('GENERAL','SCREENING','INTERVIEW','HR','REFERENCE_CHECK','OFFER'))
);

CREATE INDEX ix_candidate_notes_candidate
    ON candidate_notes (tenant_id, candidate_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Candidate timeline (append-only)
-- =====================================================================
CREATE TABLE candidate_timeline_events (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    candidate_id   UUID        NOT NULL REFERENCES candidates (id),
    application_id UUID,
    event_type     VARCHAR(64) NOT NULL,
    event_summary  VARCHAR(1024),
    event_data     TEXT,
    actor_id       UUID,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);

CREATE INDEX ix_candidate_timeline_candidate
    ON candidate_timeline_events (tenant_id, candidate_id, occurred_at DESC);

CREATE INDEX ix_candidate_timeline_application
    ON candidate_timeline_events (tenant_id, application_id)
    WHERE application_id IS NOT NULL;

-- =====================================================================
-- Candidate communications
-- =====================================================================
CREATE TABLE candidate_communications (
    id             UUID        PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    candidate_id   UUID        NOT NULL REFERENCES candidates (id),
    application_id UUID,
    channel        VARCHAR(32) NOT NULL,
    direction      VARCHAR(16) NOT NULL,
    subject        VARCHAR(512),
    body_summary   VARCHAR(4000),
    external_ref   VARCHAR(512),
    occurred_at    TIMESTAMPTZ NOT NULL,
    sent_by        UUID,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID,
    deleted_at     TIMESTAMPTZ,
    version_no     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_candidate_communications_channel
        CHECK (channel IN ('EMAIL','PHONE','SMS','WHATSAPP','IN_PERSON','VIDEO','LINKEDIN','OTHER')),
    CONSTRAINT ck_candidate_communications_direction
        CHECK (direction IN ('INBOUND','OUTBOUND'))
);

CREATE INDEX ix_candidate_communications_candidate
    ON candidate_communications (tenant_id, candidate_id, occurred_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_candidate_communications_application
    ON candidate_communications (tenant_id, application_id)
    WHERE deleted_at IS NULL AND application_id IS NOT NULL;

-- =====================================================================
-- Job applications
-- =====================================================================
CREATE TABLE job_applications (
    id                       UUID        PRIMARY KEY,
    tenant_id                UUID        NOT NULL,
    company_id               UUID        NOT NULL,
    application_number       VARCHAR(64) NOT NULL,
    candidate_id             UUID        NOT NULL REFERENCES candidates (id),
    job_requisition_id       UUID        NOT NULL REFERENCES job_requisitions (id),
    resume_id                UUID        REFERENCES candidate_resumes (id),
    source                   VARCHAR(32) NOT NULL,
    source_details           VARCHAR(512),
    referred_by_employee_id  UUID        REFERENCES employees (id),
    status                   VARCHAR(32) NOT NULL DEFAULT 'NEW',
    workflow_instance_id     UUID,
    applied_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    screened_at              TIMESTAMPTZ,
    decided_at               TIMESTAMPTZ,
    decided_by               UUID,
    decision_notes           VARCHAR(4000),
    rejection_reason         VARCHAR(64),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by               UUID,
    updated_by               UUID,
    deleted_at               TIMESTAMPTZ,
    version_no               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_applications_status
        CHECK (status IN (
            'NEW','SCREENING','SHORTLISTED',
            'INTERVIEW_SCHEDULED','INTERVIEWING','INTERVIEW_COMPLETED',
            'OFFER_INITIATED','OFFER_EXTENDED','OFFER_ACCEPTED','OFFER_DECLINED',
            'HIRED','ONBOARDING',
            'ON_HOLD','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_job_applications_source
        CHECK (source IN (
            'REFERRAL','JOB_BOARD','AGENCY','CAMPUS','DIRECT','INTERNAL',
            'WEBSITE','LINKEDIN','SOCIAL_MEDIA','EVENT','WALK_IN','OTHER')),
    CONSTRAINT ck_job_applications_rejection_reason
        CHECK (rejection_reason IS NULL OR rejection_reason IN (
            'NOT_QUALIFIED','POOR_INTERVIEW','COMPENSATION_MISMATCH',
            'LOCATION_MISMATCH','EXPERIENCE_MISMATCH','POSITION_CLOSED',
            'CANDIDATE_WITHDREW','DUPLICATE','BACKGROUND_CHECK_FAILED','OTHER'))
);

CREATE UNIQUE INDEX ux_job_applications_number_alive
    ON job_applications (tenant_id, company_id, LOWER(application_number))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_job_applications_candidate_requisition_alive
    ON job_applications (tenant_id, company_id, candidate_id, job_requisition_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_applications_status
    ON job_applications (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_applications_requisition
    ON job_applications (tenant_id, company_id, job_requisition_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_applications_candidate
    ON job_applications (tenant_id, candidate_id)
    WHERE deleted_at IS NULL;

-- Backfill FK for communications / timeline once applications exist.
ALTER TABLE candidate_communications
    ADD CONSTRAINT fk_candidate_communications_application
    FOREIGN KEY (application_id) REFERENCES job_applications (id);

ALTER TABLE candidate_timeline_events
    ADD CONSTRAINT fk_candidate_timeline_application
    FOREIGN KEY (application_id) REFERENCES job_applications (id);

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'ATS_READ',        'View candidates, resumes, applications, timelines'),
    (gen_random_uuid(), 'ATS_WRITE',       'Create and edit candidates, resumes, tags, notes, applications'),
    (gen_random_uuid(), 'ATS_COMMUNICATE', 'Log candidate communications (email, phone, meeting notes)'),
    (gen_random_uuid(), 'ATS_ADMIN',       'Administer ATS configuration; hard-delete data');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ATS_READ','ATS_WRITE','ATS_COMMUNICATE','ATS_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
