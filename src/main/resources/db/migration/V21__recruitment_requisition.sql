-- Recruitment & Job Requisition (T1 — Volume 5)
--
-- Foundation of the Talent Management stack. Introduces two aggregates:
--
--   * job_positions      — a named seat in the org (title + department + grade band). Positions
--                          are long-lived; multiple requisitions can be raised against one
--                          position over its lifetime (backfills, expansions, replacements).
--   * job_requisitions   — a hiring request for one or more headcount against a position.
--                          Approval routes through the workflow engine (subject_type =
--                          'recruitment.requisition'). Approved requisitions turn OPEN and drive
--                          downstream milestones (ATS, interviews, offers, onboarding).
--
-- Multi-tenant + multi-company. Soft-delete + optimistic locking on both aggregates.

-- =====================================================================
-- Job positions
-- =====================================================================
CREATE TABLE job_positions (
    id                     UUID        PRIMARY KEY,
    tenant_id              UUID        NOT NULL,
    company_id             UUID        NOT NULL,
    code                   VARCHAR(64) NOT NULL,
    title                  VARCHAR(256) NOT NULL,
    description            VARCHAR(4000),
    department_org_unit_id UUID        REFERENCES organization_units (id),
    location               VARCHAR(256),
    employment_type        VARCHAR(32) NOT NULL,
    grade                  VARCHAR(64),
    salary_currency        VARCHAR(3),
    salary_min             NUMERIC(18,2),
    salary_max             NUMERIC(18,2),
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by             UUID,
    updated_by             UUID,
    deleted_at             TIMESTAMPTZ,
    version_no             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_positions_employment_type
        CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERN','CONSULTANT')),
    CONSTRAINT ck_job_positions_salary_currency_len
        CHECK (salary_currency IS NULL OR LENGTH(salary_currency) = 3),
    CONSTRAINT ck_job_positions_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),
    CONSTRAINT ck_job_positions_salary_non_negative
        CHECK ((salary_min IS NULL OR salary_min >= 0)
           AND (salary_max IS NULL OR salary_max >= 0))
);

CREATE UNIQUE INDEX ux_job_positions_company_code_alive
    ON job_positions (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_positions_department
    ON job_positions (tenant_id, company_id, department_org_unit_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_positions_active
    ON job_positions (tenant_id, company_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Job requisitions
-- =====================================================================
CREATE TABLE job_requisitions (
    id                      UUID        PRIMARY KEY,
    tenant_id               UUID        NOT NULL,
    company_id              UUID        NOT NULL,
    requisition_number      VARCHAR(64) NOT NULL,
    job_position_id         UUID        NOT NULL REFERENCES job_positions (id),
    title                   VARCHAR(256) NOT NULL,
    department_org_unit_id  UUID        REFERENCES organization_units (id),
    location                VARCHAR(256),
    employment_type         VARCHAR(32) NOT NULL,
    headcount               INT         NOT NULL DEFAULT 1,
    filled_count            INT         NOT NULL DEFAULT 0,
    priority                VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    justification           VARCHAR(4000),
    hiring_manager_id       UUID        REFERENCES employees (id),
    recruiter_id            UUID        REFERENCES employees (id),
    target_start_date       DATE,
    budget_currency         VARCHAR(3),
    budget_amount           NUMERIC(18,2),
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    workflow_instance_id    UUID,
    submitted_at            TIMESTAMPTZ,
    decided_at              TIMESTAMPTZ,
    decided_by              UUID,
    decision_notes          VARCHAR(2000),
    opened_at               TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    closed_reason           VARCHAR(2000),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              UUID,
    updated_by              UUID,
    deleted_at              TIMESTAMPTZ,
    version_no              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_requisitions_status
        CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','OPEN','ON_HOLD','FILLED','CLOSED','CANCELLED')),
    CONSTRAINT ck_job_requisitions_priority
        CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_job_requisitions_employment_type
        CHECK (employment_type IN ('FULL_TIME','PART_TIME','CONTRACT','TEMPORARY','INTERN','CONSULTANT')),
    CONSTRAINT ck_job_requisitions_headcount
        CHECK (headcount >= 1),
    CONSTRAINT ck_job_requisitions_filled_count
        CHECK (filled_count >= 0 AND filled_count <= headcount),
    CONSTRAINT ck_job_requisitions_budget_currency_len
        CHECK (budget_currency IS NULL OR LENGTH(budget_currency) = 3),
    CONSTRAINT ck_job_requisitions_budget_non_negative
        CHECK (budget_amount IS NULL OR budget_amount >= 0)
);

CREATE UNIQUE INDEX ux_job_requisitions_number_alive
    ON job_requisitions (tenant_id, company_id, LOWER(requisition_number))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_requisitions_position
    ON job_requisitions (tenant_id, company_id, job_position_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_requisitions_status
    ON job_requisitions (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_job_requisitions_hiring_manager
    ON job_requisitions (tenant_id, company_id, hiring_manager_id)
    WHERE deleted_at IS NULL AND hiring_manager_id IS NOT NULL;

CREATE INDEX ix_job_requisitions_recruiter
    ON job_requisitions (tenant_id, company_id, recruiter_id)
    WHERE deleted_at IS NULL AND recruiter_id IS NOT NULL;

CREATE INDEX ix_job_requisitions_workflow
    ON job_requisitions (workflow_instance_id)
    WHERE deleted_at IS NULL AND workflow_instance_id IS NOT NULL;

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'RECRUITMENT_READ',    'View job positions and requisitions'),
    (gen_random_uuid(), 'RECRUITMENT_WRITE',   'Create and edit job positions and requisitions'),
    (gen_random_uuid(), 'RECRUITMENT_APPROVE', 'Approve or reject submitted requisitions'),
    (gen_random_uuid(), 'RECRUITMENT_ADMIN',   'Administer recruitment configuration (positions, close/cancel any requisition)');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('RECRUITMENT_READ','RECRUITMENT_WRITE','RECRUITMENT_APPROVE','RECRUITMENT_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
