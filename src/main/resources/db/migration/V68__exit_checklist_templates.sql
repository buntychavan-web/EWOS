-- Sprint 26 — Exit Management V1, increment 4.
--
-- Configurable exit clearance checklist: a named, company- (and optionally org-unit-) scoped set
-- of checklist items, auto-applied to a resignation's clearance list once it's accepted. Business
-- unit and department are both organization_units rows in this schema, so a single optional
-- org_unit_id scope covers both; there is no grade/designation/employee-category master data in
-- EWOS today, so scoping by those is deferred (see COMPANY_CONFIGURATION_BACKLOG.md-style backlog
-- note in the Sprint 26 report, not implemented here).
CREATE TABLE exit_checklist_templates (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    company_id    UUID        NOT NULL,
    org_unit_id   UUID,
    name          VARCHAR(200) NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version_no    BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX idx_exit_checklist_templates_lookup
    ON exit_checklist_templates (tenant_id, company_id, org_unit_id)
    WHERE deleted_at IS NULL;

CREATE TABLE exit_checklist_template_items (
    id            UUID        PRIMARY KEY,
    tenant_id     UUID        NOT NULL,
    template_id   UUID        NOT NULL REFERENCES exit_checklist_templates (id) ON DELETE CASCADE,
    department    VARCHAR(32) NOT NULL,
    item_name     VARCHAR(200) NOT NULL,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID,
    deleted_at    TIMESTAMPTZ,
    version_no    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_checklist_item_department
        CHECK (department IN ('IT','FINANCE','HR','MANAGER','ADMIN','COMPLIANCE'))
);

CREATE INDEX idx_exit_checklist_template_items_template
    ON exit_checklist_template_items (tenant_id, template_id)
    WHERE deleted_at IS NULL;

-- Distinguishes multiple clearance items within the same department (e.g. "Laptop" and "Mobile"
-- both routed to IT). Null preserves the pre-Sprint-26 one-clearance-per-department shape.
ALTER TABLE exit_clearances ADD COLUMN item_name VARCHAR(200);
