-- Career & Succession Planning (T11 — Volume 5)
--
-- Career pathways, promotion eligibility, talent pools, successor plans for key positions, HiPo /
-- readiness assessments.

CREATE TABLE career_paths (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    from_designation      VARCHAR(256) NOT NULL,
    to_designation        VARCHAR(256) NOT NULL,
    min_tenure_months     INT,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_career_paths_code_alive
    ON career_paths (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE promotion_eligibility (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    career_path_id        UUID        REFERENCES career_paths (id),
    eligible              BOOLEAN     NOT NULL DEFAULT FALSE,
    tenure_months         INT,
    last_rating           NUMERIC(5,2),
    competency_gap        INT,
    notes                 VARCHAR(4000),
    assessed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assessed_by           UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_promotion_eligibility_employee
    ON promotion_eligibility (tenant_id, employee_id, assessed_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE talent_pools (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    tier                  VARCHAR(32),
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_talent_pools_tier
        CHECK (tier IS NULL OR tier IN
            ('HIGH_POTENTIAL','HIGH_PERFORMER','SOLID_CONTRIBUTOR','DEVELOPING','UNDERPERFORMER'))
);

CREATE UNIQUE INDEX ux_talent_pools_code_alive
    ON talent_pools (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE talent_pool_members (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    pool_id               UUID        NOT NULL REFERENCES talent_pools (id) ON DELETE CASCADE,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    added_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by              UUID,
    removed_at            TIMESTAMPTZ,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_talent_pool_members_pool
    ON talent_pool_members (tenant_id, pool_id)
    WHERE deleted_at IS NULL AND removed_at IS NULL;

CREATE TABLE successor_plans (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    position_title        VARCHAR(256) NOT NULL,
    incumbent_employee_id UUID        REFERENCES employees (id),
    org_unit_id           UUID,
    notes                 VARCHAR(4000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_successor_plans_position
    ON successor_plans (tenant_id, company_id, LOWER(position_title))
    WHERE deleted_at IS NULL;

CREATE TABLE successor_candidates (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    plan_id               UUID        NOT NULL REFERENCES successor_plans (id) ON DELETE CASCADE,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    priority              INT         NOT NULL DEFAULT 1,
    readiness             VARCHAR(32) NOT NULL,
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_successor_readiness
        CHECK (readiness IN ('READY_NOW','READY_1_YEAR','READY_2_YEAR','NOT_READY'))
);

CREATE INDEX ix_successor_candidates_plan
    ON successor_candidates (tenant_id, plan_id, priority)
    WHERE deleted_at IS NULL;

CREATE TABLE readiness_assessments (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    performance_score     NUMERIC(5,2),
    potential_score       NUMERIC(5,2),
    tier                  VARCHAR(32),
    readiness             VARCHAR(32),
    notes                 VARCHAR(4000),
    assessed_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assessed_by           UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_readiness_tier
        CHECK (tier IS NULL OR tier IN
            ('HIGH_POTENTIAL','HIGH_PERFORMER','SOLID_CONTRIBUTOR','DEVELOPING','UNDERPERFORMER')),
    CONSTRAINT ck_readiness_level
        CHECK (readiness IS NULL OR readiness IN
            ('READY_NOW','READY_1_YEAR','READY_2_YEAR','NOT_READY'))
);

CREATE INDEX ix_readiness_assessments_employee
    ON readiness_assessments (tenant_id, employee_id, assessed_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_readiness_assessments_tier
    ON readiness_assessments (tenant_id, company_id, tier)
    WHERE deleted_at IS NULL AND tier IS NOT NULL;

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'SUCCESSION_READ',    'View career paths, pools, plans, assessments'),
    (gen_random_uuid(), 'SUCCESSION_WRITE',   'Manage career paths, pools, successor plans'),
    (gen_random_uuid(), 'SUCCESSION_ASSESS',  'Record eligibility + readiness assessments'),
    (gen_random_uuid(), 'SUCCESSION_ADMIN',   'Administer succession module');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('SUCCESSION_READ','SUCCESSION_WRITE','SUCCESSION_ASSESS','SUCCESSION_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
