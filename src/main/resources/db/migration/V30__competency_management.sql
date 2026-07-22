-- Competency Management (T10 — Volume 5)
--
-- Competency library, role-level requirements, per-employee proficiency, assessments (self /
-- manager / peer / external), gap analysis, and development plans with actionable steps.

CREATE TABLE competencies (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    category              VARCHAR(64),
    scale_min             INT         NOT NULL DEFAULT 1,
    scale_max             INT         NOT NULL DEFAULT 5,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_competencies_scale CHECK (scale_max > scale_min)
);

CREATE UNIQUE INDEX ux_competencies_code_alive
    ON competencies (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE role_competencies (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    org_unit_id           UUID,
    designation           VARCHAR(256),
    competency_id         UUID        NOT NULL REFERENCES competencies (id),
    required_level        INT         NOT NULL,
    weightage             NUMERIC(5,2),
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_role_competency_level CHECK (required_level >= 0)
);

CREATE INDEX ix_role_competencies_designation
    ON role_competencies (tenant_id, company_id, designation)
    WHERE deleted_at IS NULL AND designation IS NOT NULL;

CREATE INDEX ix_role_competencies_org
    ON role_competencies (tenant_id, company_id, org_unit_id)
    WHERE deleted_at IS NULL AND org_unit_id IS NOT NULL;

CREATE TABLE employee_competencies (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    competency_id         UUID        NOT NULL REFERENCES competencies (id),
    current_level         INT         NOT NULL,
    target_level          INT,
    last_assessed_at      TIMESTAMPTZ,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employee_competency_level CHECK (current_level >= 0)
);

CREATE UNIQUE INDEX ux_employee_competencies_unique
    ON employee_competencies (tenant_id, employee_id, competency_id)
    WHERE deleted_at IS NULL;

CREATE TABLE competency_assessments (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    competency_id         UUID        NOT NULL REFERENCES competencies (id),
    assessment_type       VARCHAR(16) NOT NULL,
    assessed_level        INT         NOT NULL,
    assessed_by           UUID,
    assessor_name         VARCHAR(256),
    comments              VARCHAR(4000),
    assessed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_assessment_type
        CHECK (assessment_type IN ('SELF','MANAGER','PEER','EXTERNAL')),
    CONSTRAINT ck_assessment_level CHECK (assessed_level >= 0)
);

CREATE INDEX ix_competency_assessments_employee
    ON competency_assessments (tenant_id, employee_id, assessed_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE development_plans (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    title                 VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    starts_on             DATE,
    ends_on               DATE,
    status                VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    completed_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_plan_status
        CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','CANCELLED'))
);

CREATE INDEX ix_dev_plans_employee
    ON development_plans (tenant_id, employee_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE development_actions (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    plan_id               UUID        NOT NULL REFERENCES development_plans (id) ON DELETE CASCADE,
    competency_id         UUID        REFERENCES competencies (id),
    action                VARCHAR(2000) NOT NULL,
    due_on                DATE,
    completed_at          TIMESTAMPTZ,
    completed             BOOLEAN     NOT NULL DEFAULT FALSE,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_dev_actions_plan
    ON development_actions (tenant_id, plan_id)
    WHERE deleted_at IS NULL;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'COMPETENCY_READ',      'View competency library, matrix, plans'),
    (gen_random_uuid(), 'COMPETENCY_WRITE',     'Create + edit competencies, role competencies'),
    (gen_random_uuid(), 'COMPETENCY_ASSESS',    'Record competency assessments'),
    (gen_random_uuid(), 'COMPETENCY_PLAN',      'Create + manage development plans'),
    (gen_random_uuid(), 'COMPETENCY_ADMIN',     'Administer the competency library');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('COMPETENCY_READ','COMPETENCY_WRITE','COMPETENCY_ASSESS','COMPETENCY_PLAN','COMPETENCY_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
