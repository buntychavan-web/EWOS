-- Sprint 1.1 — Tenant Resolution. Closes the platform's most significant identified security
-- gap: `users` carried no tenant_id and the JWT carried no tenant claim, so every one of the
-- ~88 controllers reading X-Tenant-Id trusted a client-supplied header with zero server-side
-- verification (see EWOS Master Baseline v1.0 and the Sprint 1 Tenant Model Architecture Review).
--
-- Per the approved architecture review, this does NOT add a `users.tenant_id` column. It adds a
-- `user_tenant_memberships` join table instead, constrained to exactly one active row per user
-- today (identical practical behavior to a column, zero UI/JWT-shape cost), so a future move to
-- genuine multi-membership is an additive constraint change, not a schema/JWT redesign — the same
-- reasoning `CompanySwitcher` already proves out one level down at the company boundary.
--
-- Cross-tenant platform-support access is NOT modeled as a bypass authority (too broad, no
-- per-tenant scope, no audit trail). It is modeled as `tenant_access_grants`: named, time-boxed,
-- revocable, and auditable.

-- =====================================================================
-- user_tenant_memberships — which tenant a user belongs to. No hard FK to identity.users:
-- same soft-reference convention already used by client_assignments.user_id.
-- =====================================================================
CREATE TABLE user_tenant_memberships (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    is_primary      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    deleted_at      TIMESTAMPTZ,
    version_no      BIGINT      NOT NULL DEFAULT 0
);

-- The "exactly one active tenant per user" business rule, enforced today as a partial unique
-- index (not a schema ceiling) — lifting this later is the entire migration a genuine
-- multi-membership future would need.
CREATE UNIQUE INDEX ux_user_tenant_memberships_user_alive
    ON user_tenant_memberships (user_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_user_tenant_memberships_tenant
    ON user_tenant_memberships (tenant_id) WHERE deleted_at IS NULL;

-- Backfill: every existing user belongs to the bootstrap tenant already seeded in V34.
INSERT INTO user_tenant_memberships (id, user_id, tenant_id, is_primary)
SELECT gen_random_uuid(), u.id, '00000000-0000-0000-0000-000000000001', TRUE
FROM users u
WHERE u.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_tenant_memberships m
      WHERE m.user_id = u.id AND m.deleted_at IS NULL
  );

-- =====================================================================
-- tenant_access_grants — narrow, time-boxed, audited exception to a user's own tenant
-- membership. Not soft-deleted: a revoked grant must stay visible for audit, so revocation is a
-- column (revoked_at/revoked_by), not @SQLDelete.
-- =====================================================================
CREATE TABLE tenant_access_grants (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL,
    tenant_id       UUID         NOT NULL REFERENCES tenants (id),
    granted_by      UUID         NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version_no      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tenant_access_grants_expires_future CHECK (expires_at > created_at)
);

CREATE INDEX ix_tenant_access_grants_user_tenant
    ON tenant_access_grants (user_id, tenant_id);

CREATE INDEX ix_tenant_access_grants_expires
    ON tenant_access_grants (expires_at);

-- =====================================================================
-- Permissions
-- =====================================================================
INSERT INTO permissions (id, code, description)
VALUES
    (gen_random_uuid(), 'TENANT_ACCESS_GRANT_READ',  'Read tenant access grants'),
    (gen_random_uuid(), 'TENANT_ACCESS_GRANT_WRITE', 'Create and revoke tenant access grants');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('TENANT_ACCESS_GRANT_READ','TENANT_ACCESS_GRANT_WRITE')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
