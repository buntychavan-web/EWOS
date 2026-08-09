-- Sprint 27C — seeds sensible default-visible fields for every existing tenant so the My Team
-- feature (MssFieldVisibilityService.canManagerView, default-deny) isn't 100% masked out of the
-- box. V72__mss_field_visibility_config.sql created the table with a PARTIAL unique index:
--
--   CREATE UNIQUE INDEX ux_mss_field_visibility_tenant_field
--       ON mss_field_visibility_config (tenant_id, field_name)
--       WHERE deleted_at IS NULL;
--
-- PostgreSQL 16 cannot infer a partial unique index for ON CONFLICT without restating its WHERE
-- predicate — "ON CONFLICT (tenant_id, field_name) DO NOTHING" alone raises "there is no unique
-- or exclusion constraint matching the ON CONFLICT specification". Every INSERT below therefore
-- uses the corrected form: "ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO
-- NOTHING", verified idempotent (re-running produces zero new rows; a soft-deleted row is not
-- covered by the partial index, so a new active row can still be created after a soft-delete,
-- which is the intended V72 design).
--
-- Fields NOT seeded here (salary, bankAccountNumber, panNumber, uan, aadhaar, etc.) stay
-- default-deny, per MssFieldVisibilityService.canManagerView's existing behavior for any field
-- with no config row.

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'employeeNumber', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'displayName', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'workEmail', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'phone', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'dateOfBirth', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'hireDate', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'status', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'primaryOrgUnitName', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'managerName', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;

INSERT INTO mss_field_visibility_config
    (id, tenant_id, field_name, manager_can_view, version_no)
SELECT gen_random_uuid(), t.id, 'employmentTypeName', true, 0
FROM tenants t
ON CONFLICT (tenant_id, field_name) WHERE deleted_at IS NULL DO NOTHING;
