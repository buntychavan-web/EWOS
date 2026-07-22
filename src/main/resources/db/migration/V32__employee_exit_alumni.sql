-- Employee Exit & Alumni (T12 — Volume 5)
--
-- Resignation, notice, buyout, exit clearance across departments, knowledge transfer log, exit
-- interview, relieving + experience letters, alumni records, rehire eligibility.

CREATE TABLE resignations (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    submitted_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_by          UUID,
    intended_last_day     DATE,
    reason                VARCHAR(2000),
    notice_period_days    INT         NOT NULL DEFAULT 0,
    notice_start_date     DATE,
    notice_end_date       DATE,
    buyout_days           INT,
    buyout_amount         NUMERIC(14,2),
    accepted_at           TIMESTAMPTZ,
    accepted_by           UUID,
    exit_workflow_instance_id UUID,
    status                VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    actual_last_day       DATE,
    rehire_eligibility    VARCHAR(32),
    rehire_notes          VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_resignation_status
        CHECK (status IN ('SUBMITTED','ACCEPTED','IN_NOTICE','EXITED','WITHDRAWN','CANCELLED')),
    CONSTRAINT ck_rehire_eligibility
        CHECK (rehire_eligibility IS NULL
               OR rehire_eligibility IN ('YES','NO','WITH_APPROVAL'))
);

CREATE UNIQUE INDEX ux_resignations_open_employee
    ON resignations (tenant_id, employee_id)
    WHERE deleted_at IS NULL AND status NOT IN ('WITHDRAWN','CANCELLED');

CREATE INDEX ix_resignations_status
    ON resignations (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE exit_clearances (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    resignation_id        UUID        NOT NULL REFERENCES resignations (id) ON DELETE CASCADE,
    department            VARCHAR(32) NOT NULL,
    owner_employee_id     UUID,
    status                VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    cleared_at            TIMESTAMPTZ,
    cleared_by            UUID,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_clearance_department
        CHECK (department IN ('IT','FINANCE','HR','MANAGER','ADMIN','COMPLIANCE')),
    CONSTRAINT ck_clearance_status
        CHECK (status IN ('PENDING','IN_PROGRESS','CLEARED','BLOCKED'))
);

CREATE INDEX ix_exit_clearances_resignation
    ON exit_clearances (tenant_id, resignation_id)
    WHERE deleted_at IS NULL;

CREATE TABLE knowledge_transfer_items (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    resignation_id        UUID        NOT NULL REFERENCES resignations (id) ON DELETE CASCADE,
    topic                 VARCHAR(512) NOT NULL,
    description           VARCHAR(4000),
    transferred_to        UUID,
    completed             BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at          TIMESTAMPTZ,
    completed_by          UUID,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_kt_items_resignation
    ON knowledge_transfer_items (tenant_id, resignation_id)
    WHERE deleted_at IS NULL;

CREATE TABLE exit_interviews (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    resignation_id        UUID        NOT NULL REFERENCES resignations (id) ON DELETE CASCADE,
    conducted_at          TIMESTAMPTZ,
    conducted_by          UUID,
    interviewer_name      VARCHAR(256),
    rating                NUMERIC(5,2),
    would_recommend       BOOLEAN,
    responses_json        VARCHAR(8000),
    comments              VARCHAR(4000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_exit_interviews_resignation
    ON exit_interviews (tenant_id, resignation_id)
    WHERE deleted_at IS NULL;

CREATE TABLE exit_documents (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    resignation_id        UUID        NOT NULL REFERENCES resignations (id) ON DELETE CASCADE,
    document_type         VARCHAR(32) NOT NULL,
    document_uri          VARCHAR(1024) NOT NULL,
    issued_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    issued_by             UUID,
    reference_number      VARCHAR(128),
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_exit_doc_type
        CHECK (document_type IN
            ('RELIEVING_LETTER','EXPERIENCE_LETTER','FNF_STATEMENT','PF_STATEMENT','OTHER'))
);

CREATE INDEX ix_exit_documents_resignation
    ON exit_documents (tenant_id, resignation_id, document_type)
    WHERE deleted_at IS NULL;

CREATE TABLE alumni_records (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    resignation_id        UUID        REFERENCES resignations (id),
    exited_on             DATE        NOT NULL,
    alumni_email          VARCHAR(256),
    linkedin_url          VARCHAR(512),
    current_employer      VARCHAR(256),
    stay_in_touch         BOOLEAN     NOT NULL DEFAULT TRUE,
    rehire_eligibility    VARCHAR(32),
    notes                 VARCHAR(4000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_alumni_rehire
        CHECK (rehire_eligibility IS NULL
               OR rehire_eligibility IN ('YES','NO','WITH_APPROVAL'))
);

CREATE UNIQUE INDEX ux_alumni_records_employee
    ON alumni_records (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'EXIT_READ',     'View resignations, clearance, interviews, alumni'),
    (gen_random_uuid(), 'EXIT_WRITE',    'Create + manage resignations + clearance'),
    (gen_random_uuid(), 'EXIT_APPROVE',  'Accept resignations + set rehire eligibility'),
    (gen_random_uuid(), 'EXIT_CLEAR',    'Record clearance for a department'),
    (gen_random_uuid(), 'EXIT_INTERVIEW','Conduct exit interviews'),
    (gen_random_uuid(), 'EXIT_ISSUE_DOC','Issue relieving / experience letters'),
    (gen_random_uuid(), 'ALUMNI_MANAGE', 'Manage alumni records'),
    (gen_random_uuid(), 'EXIT_ADMIN',    'Administer exit module');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('EXIT_READ','EXIT_WRITE','EXIT_APPROVE','EXIT_CLEAR','EXIT_INTERVIEW','EXIT_ISSUE_DOC',
     'ALUMNI_MANAGE','EXIT_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
