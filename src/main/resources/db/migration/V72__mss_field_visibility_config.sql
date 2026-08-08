-- Sprint 27A — ESS/MSS foundation, item 5.
--
-- Field-level ACL matrix for Manager Self Service drill-downs (PRD §12, audit finding 3.2):
-- per-tenant, per-field configuration of whether a manager may see a direct report's field
-- (salary, bank details, PAN, full residential address, ...) when a future sub-sprint (27C)
-- builds the "My Team" drill-down. Default-deny: a field with no row here is masked — see
-- MssFieldVisibilityService.canManagerView.
--
-- No consuming endpoint exists yet in this sprint; this migration and the entity/service that
-- use it are foundation only, ready for 27C to read from.
CREATE TABLE mss_field_visibility_config (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    field_name        VARCHAR(100) NOT NULL,
    manager_can_view  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    deleted_at        TIMESTAMPTZ,
    version_no        BIGINT      NOT NULL DEFAULT 0
);

-- Partial unique index (not a full-column UNIQUE) so a field's visibility can be reconfigured
-- (soft-delete the old row, insert a new one) without a live-row collision — same pattern as
-- every other soft-deletable config table in this codebase (CONTRIBUTING.md §4).
CREATE UNIQUE INDEX ux_mss_field_visibility_tenant_field
    ON mss_field_visibility_config (tenant_id, field_name)
    WHERE deleted_at IS NULL;
