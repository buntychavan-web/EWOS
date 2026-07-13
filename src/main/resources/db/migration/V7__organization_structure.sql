-- Sprint 7: Organization Structure Engine.
--
-- Introduces a configurable Organization structure:
--
--   * organization_levels — customer-configurable level definitions
--     (e.g. "Business Unit", "Division", "Region", "Branch"). No level names
--     are hard-coded; customers configure them per tenant. Levels form a
--     parent-child hierarchy driven by display_sequence + parent_level_id.
--
--   * organization_nodes — the actual nodes making up the org tree. Every
--     node references an organization_level and, unless it is a root,
--     a parent node.
--
--   * organization_node_versions — immutable audit trail of every structural
--     change a node undergoes (create / rename / move / merge_into /
--     split_from / deactivate). One row per change. History is never lost.
--
--   * organization_node_inheritance_overrides — per-node override for an
--     inheritable resource (policies, cost/profit centres, statutory refs,
--     workflows, shared services). Effective-dated. When absent, the node
--     inherits from its parent chain.
--
-- Employees (introduced in a later sprint) will FK to organization_nodes
-- only — Region / Department / Branch are NEVER stored on the employee row.
-- Moving a node therefore automatically re-parents every referencing
-- employee without touching employee data.

-- ---------- organization_levels ----------
CREATE TABLE organization_levels (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    display_sequence    INT          NOT NULL,
    parent_level_id     UUID         REFERENCES organization_levels(id) ON DELETE RESTRICT,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT ck_org_levels_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_org_levels_tenant ON organization_levels(tenant_id);
CREATE UNIQUE INDEX ux_org_levels_tenant_code_active
    ON organization_levels(tenant_id, code) WHERE deleted_at IS NULL;
CREATE INDEX idx_org_levels_parent ON organization_levels(parent_level_id);

-- ---------- organization_nodes ----------
CREATE TABLE organization_nodes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    level_id          UUID         NOT NULL REFERENCES organization_levels(id) ON DELETE RESTRICT,
    parent_node_id    UUID         REFERENCES organization_nodes(id) ON DELETE RESTRICT,
    code              VARCHAR(50)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_org_nodes_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_org_nodes_no_self_parent CHECK (parent_node_id IS NULL OR parent_node_id <> id)
);
CREATE INDEX idx_org_nodes_tenant ON organization_nodes(tenant_id);
CREATE INDEX idx_org_nodes_parent ON organization_nodes(parent_node_id);
CREATE INDEX idx_org_nodes_level ON organization_nodes(level_id);
CREATE UNIQUE INDEX ux_org_nodes_tenant_code_active
    ON organization_nodes(tenant_id, code) WHERE deleted_at IS NULL;

-- ---------- organization_node_versions ----------
-- Append-only history of structural changes. One row per change; the
-- previous shape is preserved by change_type + snapshot columns. The service
-- layer writes a version row every time a node is created, renamed, moved,
-- merged, split, or deactivated. Historical structure reconstruction reads
-- this table.
CREATE TABLE organization_node_versions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id              UUID         NOT NULL REFERENCES organization_nodes(id) ON DELETE CASCADE,
    change_type          VARCHAR(30)  NOT NULL
                         CHECK (change_type IN
                             ('CREATED','RENAMED','MOVED','MERGED_INTO','SPLIT_FROM','DEACTIVATED','REACTIVATED')),
    effective_from       DATE         NOT NULL,
    effective_to         DATE,
    snapshot_code        VARCHAR(50)  NOT NULL,
    snapshot_name        VARCHAR(255) NOT NULL,
    snapshot_parent_id   UUID,
    snapshot_level_id    UUID         NOT NULL,
    related_node_id      UUID,        -- for MERGED_INTO / SPLIT_FROM the counterpart node
    notes                VARCHAR(500),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT ck_org_node_versions_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_org_node_versions_node ON organization_node_versions(node_id, effective_from DESC);

-- ---------- organization_node_inheritance_overrides ----------
-- Per-node, per-kind override for an inheritable resource. If no row exists
-- for a given (node, kind) that is live at the query date, the node inherits
-- from its parent chain; the resolver walks up until it finds a hit or falls
-- through to the company-level assignment (Sprint 6).
CREATE TABLE organization_node_inheritance_overrides (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id           UUID         NOT NULL REFERENCES organization_nodes(id) ON DELETE CASCADE,
    inheritable_kind  VARCHAR(40)  NOT NULL
                      CHECK (inheritable_kind IN
                          ('HOLIDAY_CALENDAR','PAYROLL_CALENDAR','LEAVE_POLICY',
                           'ATTENDANCE_POLICY','SHIFT_POLICY','WORKFLOW',
                           'COST_CENTRE','PROFIT_CENTRE','STATUTORY_REGISTRATION',
                           'SHARED_SERVICE')),
    override_ref      UUID         NOT NULL,
    override_label    VARCHAR(255),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_org_node_overrides_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_org_node_overrides_node_kind
    ON organization_node_inheritance_overrides(node_id, inheritable_kind, effective_from DESC);

-- ---------- Permissions ----------
INSERT INTO permissions (code, description) VALUES
    ('ORGANIZATION_READ',   'Read organization structure and inheritance'),
    ('ORGANIZATION_WRITE',  'Create, move, merge, split, and rename organization nodes and levels'),
    ('ORGANIZATION_DELETE', 'Soft-delete organization nodes and levels')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.code IN ('ORGANIZATION_READ','ORGANIZATION_WRITE','ORGANIZATION_DELETE')
ON CONFLICT DO NOTHING;
