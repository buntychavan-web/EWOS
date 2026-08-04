-- Sprint 24L item 1 — Payroll Maker-Checker (true separation of duties).
--
-- Codex CTO audit follow-up: the previous PayrollApprovalWorkflowListener/PAYROLL_CLIENT_APPROVAL
-- integration (V36) was opt-in for exactly one hard-coded bootstrap tenant, never blocked
-- finalizeRun(), and its workflow_transitions.required_role was never actually enforced anywhere.
-- This migration replaces it with a dedicated, purpose-built payroll approval domain:
--
--   payroll_approval_policies  — one row per company that has opted into maker-checker.
--   payroll_approval_levels    — the ordered hierarchy for a policy (level 1..N, each gated by a
--                                 role code resolved the same way com.ewos.workflow's
--                                 ApproverResolver already resolves role-based approvers).
--   payroll_approval_requests  — one row per payroll run submitted for approval; a snapshot of
--                                 total_levels is taken at submission time so a later policy edit
--                                 never retroactively changes an in-flight approval.
--   payroll_approval_decisions — append-only decision log (never updated/deleted) — the complete
--                                 audit trail / approval history the audit asked for.
--
-- A company with no active policy is unaffected (backward compatible, matching this module's
-- existing "seeded per company" conventions); the preparer-cannot-finalize-their-own-run rule is
-- enforced unconditionally in application code regardless of whether a policy exists.

CREATE TABLE payroll_approval_policies (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    company_id  UUID        NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  UUID,
    updated_by  UUID,
    deleted_at  TIMESTAMPTZ,
    version_no  BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_payroll_approval_policies_company_alive
    ON payroll_approval_policies (tenant_id, company_id)
    WHERE deleted_at IS NULL;

CREATE TABLE payroll_approval_levels (
    id                  UUID        PRIMARY KEY,
    policy_id           UUID        NOT NULL REFERENCES payroll_approval_policies (id) ON DELETE CASCADE,
    level_number        INTEGER     NOT NULL,
    approver_role_code  VARCHAR(128) NOT NULL,
    description         VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_approval_levels_level_number CHECK (level_number >= 1)
);

CREATE UNIQUE INDEX ux_payroll_approval_levels_policy_level
    ON payroll_approval_levels (policy_id, level_number);

CREATE TABLE payroll_approval_requests (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    company_id      UUID        NOT NULL,
    payroll_run_id  UUID        NOT NULL REFERENCES payroll_runs (id),
    policy_id       UUID        NOT NULL REFERENCES payroll_approval_policies (id),
    preparer_id     UUID        NOT NULL,
    total_levels    INTEGER     NOT NULL,
    current_level   INTEGER     NOT NULL DEFAULT 1,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID,
    updated_by      UUID,
    version_no      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_approval_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_payroll_approval_requests_levels
        CHECK (total_levels >= 1 AND current_level >= 1 AND current_level <= total_levels)
);

-- At most one approval request per run — resubmission after rejection is out of scope (Sprint 24L
-- only requires the gate to exist and be enforceable; a rejected run stays rejected).
CREATE UNIQUE INDEX ux_payroll_approval_requests_run
    ON payroll_approval_requests (payroll_run_id);

CREATE INDEX ix_payroll_approval_requests_tenant_status
    ON payroll_approval_requests (tenant_id, company_id, status);

CREATE TABLE payroll_approval_decisions (
    id                   UUID        PRIMARY KEY,
    tenant_id            UUID        NOT NULL,
    approval_request_id  UUID        NOT NULL REFERENCES payroll_approval_requests (id) ON DELETE CASCADE,
    level_number         INTEGER     NOT NULL,
    decision             VARCHAR(16) NOT NULL,
    decided_by           UUID        NOT NULL,
    decided_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    comments             VARCHAR(2000),
    CONSTRAINT ck_payroll_approval_decisions_decision
        CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX ix_payroll_approval_decisions_request
    ON payroll_approval_decisions (approval_request_id, decided_at);

-- Deciding an approval level additionally requires the level's own configured role — this
-- permission only gates who may call the decide endpoint at all (a coarse platform-level check),
-- mirroring PAYROLL_READ/PAYROLL_RUN/PAYROLL_ADMIN's existing role.
INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'PAYROLL_APPROVE', 'Decide (approve/reject) a payroll run awaiting maker-checker approval')
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'PAYROLL_APPROVE'
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
