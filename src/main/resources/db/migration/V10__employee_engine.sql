-- WP-005 Employee Engine
--
-- Employee master: the linkage aggregate between a person (biographic identity —
-- future Person module) and an organization unit (WP-004).
--
-- Design notes
--   * person_id is a soft reference — no FK yet. When the Person module lands a follow-up
--     migration adds the FK.
--   * Employees carry the biographic fields directly (first_name, last_name, ...) so the
--     module is fully usable before the Person module ships. When Person is built, those
--     fields become a denormalised cache of the effective-dated Person snapshot.
--   * employment_types is a per-tenant metadata dictionary (FULL_TIME, PART_TIME,
--     CONTRACT, INTERN, ...) — no ENUM in code, matching the platform's metadata-driven
--     rule.
--   * Soft-deleted via deleted_at; partial unique indexes let employee_number / work_email
--     be re-issued after a terminated employee is closed and soft-deleted.
--   * @Version optimistic locking via version_no.

-- =====================================================================
-- employment_types  (metadata dictionary)
-- =====================================================================
CREATE TABLE employment_types (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    sort_order      INTEGER     NOT NULL DEFAULT 100,
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_employment_types_tenant_code_alive
    ON employment_types (tenant_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_employment_types_tenant_active
    ON employment_types (tenant_id, active)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- employees
-- =====================================================================
CREATE TABLE employees (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    person_id             UUID,
    employee_number       VARCHAR(64) NOT NULL,
    first_name            VARCHAR(128) NOT NULL,
    middle_name           VARCHAR(128),
    last_name             VARCHAR(128) NOT NULL,
    display_name          VARCHAR(256),
    work_email            VARCHAR(320) NOT NULL,
    personal_email        VARCHAR(320),
    phone                 VARCHAR(32),
    date_of_birth         DATE,
    gender_code           VARCHAR(32),
    primary_org_unit_id   UUID        REFERENCES organization_units (id),
    manager_employee_id   UUID        REFERENCES employees (id),
    employment_type_id    UUID        REFERENCES employment_types (id),
    hire_date             DATE        NOT NULL,
    termination_date      DATE,
    status                VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_employees_status CHECK (status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_employees_termination_after_hire
        CHECK (termination_date IS NULL OR termination_date >= hire_date),
    CONSTRAINT ck_employees_terminated_has_date
        CHECK (status <> 'TERMINATED' OR termination_date IS NOT NULL),
    CONSTRAINT ck_employees_no_self_manager
        CHECK (manager_employee_id IS NULL OR manager_employee_id <> id)
);

CREATE UNIQUE INDEX ux_employees_tenant_company_number_alive
    ON employees (tenant_id, company_id, LOWER(employee_number))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_employees_tenant_company_work_email_alive
    ON employees (tenant_id, company_id, LOWER(work_email))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_employees_tenant_company
    ON employees (tenant_id, company_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_employees_org_unit
    ON employees (primary_org_unit_id)
    WHERE deleted_at IS NULL AND primary_org_unit_id IS NOT NULL;

CREATE INDEX ix_employees_manager
    ON employees (manager_employee_id)
    WHERE deleted_at IS NULL AND manager_employee_id IS NOT NULL;

CREATE INDEX ix_employees_tenant_status
    ON employees (tenant_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_employees_tenant_person
    ON employees (tenant_id, person_id)
    WHERE deleted_at IS NULL AND person_id IS NOT NULL;

CREATE INDEX ix_employees_tenant_hire_date
    ON employees (tenant_id, hire_date)
    WHERE deleted_at IS NULL;

-- =====================================================================
-- Identity permissions for Employee module
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'EMP_READ',  'Read employees and employment types'),
    (gen_random_uuid(), 'EMP_WRITE', 'Create and update employees and employment types'),
    (gen_random_uuid(), 'EMP_ADMIN', 'Hire, terminate, and re-assign employees');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('EMP_READ','EMP_WRITE','EMP_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
