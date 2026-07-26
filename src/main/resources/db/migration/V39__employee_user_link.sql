-- Sprint 1.3: Employee <-> User identity link.
-- Nullable, no hard FK (cross-module reference, mirrors employees.tenant_id / employees.company_id,
-- which are also plain UUID columns with no REFERENCES clause).

ALTER TABLE employees
    ADD COLUMN user_id UUID;

-- One user can be linked to at most one active employee per company (prevents accidental double-link
-- within a company); the same user_id may legitimately appear on employees in different companies
-- (multi-company payroll-provider tenants — see the Sprint 1.2 Product Owner decision on candidate
-- duplicate search for the same underlying business reality).
CREATE UNIQUE INDEX uq_employees_company_user
    ON employees (company_id, user_id)
    WHERE user_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_employees_user_id ON employees (user_id) WHERE user_id IS NOT NULL;

-- Append-only audit trail for link / unlink / provision actions on the Employee <-> User link.
-- Follows the same idiom as login_history / workflow_history / data_exchange_history: extends
-- AuditableEntity so created_by / created_at capture "performed by" / "date-time" automatically.
CREATE TABLE employee_identity_link_history (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id      UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    action           VARCHAR(20) NOT NULL,
    previous_user_id UUID,
    new_user_id      UUID,
    reason           VARCHAR(500),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID,
    updated_by       UUID
);

CREATE INDEX idx_employee_identity_link_history_employee_created
    ON employee_identity_link_history (employee_id, created_at DESC);
