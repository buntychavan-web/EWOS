-- Sprint 24L item 2 — Payroll Reopen / Correction Framework.
--
-- Two distinct "closed" states in this codebase need two distinct reopen mechanisms:
--
--   payroll_period_reopen_log       — a CLOSED PayrollPeriod's own status is flipped back to
--                                      LOCKED (append-only log of who/when/why; the period itself
--                                      carries no other closed-state data to preserve).
--   payroll_run_reopen_authorizations — a FROZEN PayrollRun's own row is NEVER mutated (its
--                                      payslips are immutable financial records — "preserve
--                                      original payroll" and "no direct data overwrite" both rule
--                                      that out). Instead this table is a time-stamped, one-time
--                                      authorization that lets exactly one new SUPPLEMENTARY run
--                                      (the existing correction mechanism, already producing its
--                                      own new, equally immutable payslips — the "reversal
--                                      entries") through PayrollRunService.startSupplementary's
--                                      post-freeze block. consumed_by_run_id gives full rollback
--                                      traceability: which correction resulted from which
--                                      authorization. The sequence of immutable PayrollRun rows
--                                      already on a period (REGULAR, then any SUPPLEMENTARY runs)
--                                      is this framework's version history — nothing new is needed
--                                      to expose it, see PayrollRunService#forPeriod.

CREATE TABLE payroll_period_reopen_log (
    id                UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL,
    company_id        UUID        NOT NULL,
    payroll_period_id UUID        NOT NULL REFERENCES payroll_periods (id),
    reason            VARCHAR(2000) NOT NULL,
    reopened_by       UUID        NOT NULL,
    reopened_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    previous_status   VARCHAR(16) NOT NULL,
    new_status        VARCHAR(16) NOT NULL
);

CREATE INDEX ix_payroll_period_reopen_log_period
    ON payroll_period_reopen_log (tenant_id, payroll_period_id, reopened_at);

CREATE TABLE payroll_run_reopen_authorizations (
    id                  UUID        PRIMARY KEY,
    tenant_id           UUID        NOT NULL,
    company_id          UUID        NOT NULL,
    payroll_run_id      UUID        NOT NULL REFERENCES payroll_runs (id),
    reason              VARCHAR(2000) NOT NULL,
    authorized_by       UUID        NOT NULL,
    authorized_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    consumed_by_run_id  UUID        REFERENCES payroll_runs (id),
    consumed_at         TIMESTAMPTZ,
    revoked_by          UUID,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    version_no          BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_payroll_reopen_auth_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED'))
);

-- At most one ACTIVE authorization per run at a time — a second reopen request while one is
-- already outstanding must revoke or consume the first, not silently stack.
CREATE UNIQUE INDEX ux_payroll_reopen_auth_one_active_per_run
    ON payroll_run_reopen_authorizations (tenant_id, payroll_run_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_payroll_reopen_auth_run
    ON payroll_run_reopen_authorizations (tenant_id, payroll_run_id, authorized_at);
