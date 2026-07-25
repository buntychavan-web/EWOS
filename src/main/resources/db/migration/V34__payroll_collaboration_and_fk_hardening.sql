-- Sprint 14.2 — Payroll Collaboration & Client-Scoped Payroll.
--
-- Two independent pieces:
--   1. payroll_collaborations — the Client<->Provider engagement/contract record.
--   2. FK hardening — the *recommended, deferred* step from the Sprint 14.1 design finally
--      lands: tenant_id/company_id columns across the platform gain real foreign keys into
--      tenants(id)/companies(id). No column is added, renamed, or removed on any existing
--      table — this is purely referential-integrity hardening.
--
-- Backward compatibility note (implementation issue discovered and resolved here, not an
-- architectural change): the frontend has been calling every tenant-scoped endpoint with a
-- hardcoded placeholder id, 00000000-0000-0000-0000-000000000001 (see DEFAULT_TENANT_ID /
-- DEFAULT_COMPANY_ID in lib/api-client.ts), because no real Tenant/Company existed before
-- Sprint 14.1. A hard, immediately-validated FK would break every one of those existing
-- flows on the next write. This migration resolves that two ways:
--   a) seeds a bootstrap Tenant + Client + Company row using that exact placeholder id, so
--      existing frontend calls keep resolving to a real row with no frontend change;
--   b) adds the new constraints as NOT VALID, so any row already in the database that
--      predates this migration with an id Postgres can't retroactively validate does not
--      block the migration itself. NOT VALID still enforces the constraint on every new
--      write from this point forward — it only skips a bulk validation pass over existing
--      rows at migration time. (A follow-up VALIDATE CONSTRAINT pass over the bootstrap
--      tenant's own rows is an operational task once real Client/Company onboarding data
--      exists, not part of this migration.)

-- =====================================================================
-- Bootstrap default tenant / client / company
-- =====================================================================
INSERT INTO tenants (id, code, name, status)
VALUES ('00000000-0000-0000-0000-000000000001', 'DEFAULT', 'Default Tenant', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO clients (id, tenant_id, code, legal_name, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'DEFAULT',
    'Default Client',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO companies (id, tenant_id, client_id, code, name, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'DEFAULT',
    'Default Company',
    'ACTIVE'
)
ON CONFLICT (id) DO NOTHING;

-- =====================================================================
-- payroll_collaborations — Client <-> Provider engagement / contract
-- =====================================================================
CREATE TABLE payroll_collaborations (
    id              UUID        PRIMARY KEY,
    client_id       UUID        NOT NULL REFERENCES clients (id),
    provider_id     UUID        NOT NULL REFERENCES payroll_service_providers (id),
    scope           VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    effective_from  DATE        NOT NULL,
    effective_to    DATE,
    sla_days        INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_collaborations_scope
        CHECK (scope IN ('FULL','STATUTORY_ONLY','REVIEW_ONLY')),
    CONSTRAINT ck_payroll_collaborations_status
        CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED')),
    CONSTRAINT ck_payroll_collaborations_effective_range
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_payroll_collaborations_sla_positive
        CHECK (sla_days IS NULL OR sla_days > 0)
);

-- One active collaboration per (client, provider) at a time.
CREATE UNIQUE INDEX ux_payroll_collaborations_client_provider_alive
    ON payroll_collaborations (client_id, provider_id)
    WHERE deleted_at IS NULL AND status = 'ACTIVE';

CREATE INDEX ix_payroll_collaborations_client
    ON payroll_collaborations (client_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_payroll_collaborations_provider
    ON payroll_collaborations (provider_id) WHERE deleted_at IS NULL;

INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'PAYROLL_COLLABORATION_READ',  'Read payroll collaborations'),
    (gen_random_uuid(), 'PAYROLL_COLLABORATION_WRITE', 'Create and update payroll collaborations');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('PAYROLL_COLLABORATION_READ','PAYROLL_COLLABORATION_WRITE')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- =====================================================================
-- FK hardening — tenant_id -> tenants(id), NOT VALID (see note above)
-- =====================================================================
ALTER TABLE employment_types             ADD CONSTRAINT fk_employment_types_tenant             FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE employees                    ADD CONSTRAINT fk_employees_tenant                     FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE organization_unit_types      ADD CONSTRAINT fk_organization_unit_types_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE organization_units           ADD CONSTRAINT fk_organization_units_tenant            FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE attendance_policies          ADD CONSTRAINT fk_attendance_policies_tenant           FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE time_entries                 ADD CONSTRAINT fk_time_entries_tenant                  FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE timesheets                   ADD CONSTRAINT fk_timesheets_tenant                    FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE leave_types                  ADD CONSTRAINT fk_leave_types_tenant                   FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE leave_allocations            ADD CONSTRAINT fk_leave_allocations_tenant             FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE leave_balances               ADD CONSTRAINT fk_leave_balances_tenant                FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE leave_requests               ADD CONSTRAINT fk_leave_requests_tenant                FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE workflow_definitions         ADD CONSTRAINT fk_workflow_definitions_tenant          FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE workflow_instances           ADD CONSTRAINT fk_workflow_instances_tenant            FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE workflow_tasks               ADD CONSTRAINT fk_workflow_tasks_tenant                FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE pay_components               ADD CONSTRAINT fk_pay_components_tenant                FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE statutory_settings           ADD CONSTRAINT fk_statutory_settings_tenant            FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE payroll_periods              ADD CONSTRAINT fk_payroll_periods_tenant               FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE payroll_runs                 ADD CONSTRAINT fk_payroll_runs_tenant                  FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE payslips                     ADD CONSTRAINT fk_payslips_tenant                      FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;
ALTER TABLE employee_compensations       ADD CONSTRAINT fk_employee_compensations_tenant        FOREIGN KEY (tenant_id) REFERENCES tenants (id) NOT VALID;

-- =====================================================================
-- FK hardening — company_id -> companies(id), NOT VALID
-- =====================================================================
ALTER TABLE employees                    ADD CONSTRAINT fk_employees_company                    FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE organization_units           ADD CONSTRAINT fk_organization_units_company            FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE pay_groups                   ADD CONSTRAINT fk_pay_groups_company                    FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payroll_periods              ADD CONSTRAINT fk_payroll_periods_company               FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payroll_runs                 ADD CONSTRAINT fk_payroll_runs_company                  FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payslips                     ADD CONSTRAINT fk_payslips_company                      FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE employee_compensations       ADD CONSTRAINT fk_employee_compensations_company        FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE employee_bank_accounts       ADD CONSTRAINT fk_employee_bank_accounts_company        FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE employee_payroll_profiles    ADD CONSTRAINT fk_employee_payroll_profiles_company     FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payroll_arrears              ADD CONSTRAINT fk_payroll_arrears_company               FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE final_settlements            ADD CONSTRAINT fk_final_settlements_company             FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE bank_advices                 ADD CONSTRAINT fk_bank_advices_company                  FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payment_instructions         ADD CONSTRAINT fk_payment_instructions_company          FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE statutory_challans           ADD CONSTRAINT fk_statutory_challans_company            FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE statutory_deductions         ADD CONSTRAINT fk_statutory_deductions_company          FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE business_units               ADD CONSTRAINT fk_business_units_company                FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE cost_centres                 ADD CONSTRAINT fk_cost_centres_company                  FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE employee_cost_allocations    ADD CONSTRAINT fk_employee_cost_allocations_company     FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE gl_accounts                  ADD CONSTRAINT fk_gl_accounts_company                   FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE gl_mappings                  ADD CONSTRAINT fk_gl_mappings_company                   FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE payroll_journals             ADD CONSTRAINT fk_payroll_journals_company              FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
ALTER TABLE scheduled_reports            ADD CONSTRAINT fk_scheduled_reports_company             FOREIGN KEY (company_id) REFERENCES companies (id) NOT VALID;
