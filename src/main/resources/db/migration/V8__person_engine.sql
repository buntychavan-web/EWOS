-- Sprint 8.1: Person Engine.
--
-- Person is the permanent identity in EWOS. It is NOT an employee. A single
-- person may hold zero or more Employment records over time (Sprint 8.2).
--
-- Tables:
--   * persons                        — master identity row. Owns the immutable
--                                       group_person_id (e.g. "P000000001") and
--                                       links back to a tenant.
--   * person_versions                — effective-dated snapshot of the core
--                                       profile. Immutable except for
--                                       effective_to. Every change writes a new
--                                       row (never overwrites).
--   * person_addresses               — permanent / current / communication,
--                                       effective-dated.
--   * person_contacts                — mobile / email contact channels,
--                                       effective-dated (personal + alternate).
--   * person_emergency_contacts      — multiple per person with priority.
--   * person_family_members          — parents / spouse / children / dependents.
--   * person_education               — qualifications, institutions, passing year.
--   * person_identity_documents      — PAN / Aadhaar / Passport / Driving Licence
--                                       / Voter ID / Other, effective-dated;
--                                       PAN and Aadhaar unique across live rows.
--   * person_id_sequence             — monotonic counter powering
--                                       group_person_id (never reused).
--   * person_duplicate_rules         — configurable duplicate-detection rules
--                                       toggled per tenant.

-- ---------- Sequence powering group_person_id ----------
CREATE SEQUENCE person_id_sequence
    INCREMENT BY 1
    START WITH 1
    MINVALUE 1
    NO MAXVALUE
    CACHE 1
    NO CYCLE;

-- ---------- persons ----------
CREATE TABLE persons (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    group_person_id     VARCHAR(20)  NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);
CREATE UNIQUE INDEX ux_persons_group_person_id ON persons(group_person_id);
CREATE INDEX idx_persons_tenant ON persons(tenant_id);

-- ---------- person_versions ----------
-- One row per profile change. Never overwritten. change_reason + changed_by +
-- approved_by capture the "why", "who requested", and "who approved" — required
-- for statutory audit.
CREATE TABLE person_versions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id             UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    version_number        INT          NOT NULL,
    effective_from        DATE         NOT NULL,
    effective_to          DATE,
    first_name            VARCHAR(100) NOT NULL,
    middle_name           VARCHAR(100),
    last_name             VARCHAR(100) NOT NULL,
    preferred_name        VARCHAR(100),
    gender                VARCHAR(30)  CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
    date_of_birth         DATE,
    marital_status        VARCHAR(20)  CHECK (marital_status IN ('SINGLE','MARRIED','DIVORCED','WIDOWED','SEPARATED','OTHER')),
    blood_group           VARCHAR(10)  CHECK (blood_group IN ('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG','O_POS','O_NEG','UNKNOWN')),
    nationality           VARCHAR(100),
    photo_url             VARCHAR(500),
    change_reason         VARCHAR(500),
    approved_by           UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    CONSTRAINT ck_person_versions_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_person_versions_person ON person_versions(person_id, effective_from DESC);
CREATE UNIQUE INDEX ux_person_versions_open ON person_versions(person_id) WHERE effective_to IS NULL;
CREATE UNIQUE INDEX ux_person_versions_number ON person_versions(person_id, version_number);
CREATE INDEX idx_person_versions_name ON person_versions(LOWER(first_name), LOWER(last_name));
CREATE INDEX idx_person_versions_dob ON person_versions(date_of_birth);

-- ---------- person_contacts ----------
CREATE TABLE person_contacts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    personal_mobile   VARCHAR(20),
    alternate_mobile  VARCHAR(20),
    personal_email    VARCHAR(255),
    alternate_email   VARCHAR(255),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_person_contacts_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_person_contacts_person ON person_contacts(person_id, effective_from DESC);
CREATE INDEX idx_person_contacts_mobile ON person_contacts(personal_mobile) WHERE personal_mobile IS NOT NULL;
CREATE INDEX idx_person_contacts_email ON person_contacts(LOWER(personal_email)) WHERE personal_email IS NOT NULL;

-- ---------- person_addresses ----------
CREATE TABLE person_addresses (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    address_kind      VARCHAR(20)  NOT NULL CHECK (address_kind IN ('PERMANENT','CURRENT','COMMUNICATION')),
    line1             VARCHAR(255) NOT NULL,
    line2             VARCHAR(255),
    city              VARCHAR(100) NOT NULL,
    state             VARCHAR(100),
    country           VARCHAR(100) NOT NULL,
    postal_code       VARCHAR(30),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_person_addresses_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_person_addresses_person_kind
    ON person_addresses(person_id, address_kind, effective_from DESC);

-- ---------- person_emergency_contacts ----------
CREATE TABLE person_emergency_contacts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    relationship      VARCHAR(60)  NOT NULL,
    priority          INT          NOT NULL DEFAULT 1,
    mobile            VARCHAR(20)  NOT NULL,
    alternate_mobile  VARCHAR(20),
    email             VARCHAR(255),
    address           VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);
CREATE INDEX idx_person_emergency_contacts_person
    ON person_emergency_contacts(person_id, priority);

-- ---------- person_family_members ----------
CREATE TABLE person_family_members (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    relation          VARCHAR(30)  NOT NULL
                      CHECK (relation IN ('FATHER','MOTHER','SPOUSE','SON','DAUGHTER','SIBLING','GUARDIAN','OTHER')),
    name              VARCHAR(255) NOT NULL,
    date_of_birth     DATE,
    gender            VARCHAR(30)  CHECK (gender IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY')),
    occupation        VARCHAR(255),
    dependent         BOOLEAN      NOT NULL DEFAULT FALSE,
    mobile            VARCHAR(20),
    email             VARCHAR(255),
    version           BIGINT       NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID
);
CREATE INDEX idx_person_family_members_person
    ON person_family_members(person_id, relation);

-- ---------- person_education ----------
CREATE TABLE person_education (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    qualification     VARCHAR(255) NOT NULL,
    institution       VARCHAR(255) NOT NULL,
    passing_year      INT          NOT NULL,
    grade             VARCHAR(60),
    specialization    VARCHAR(255),
    document_url      VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_person_education_year CHECK (passing_year BETWEEN 1900 AND 2200)
);
CREATE INDEX idx_person_education_person ON person_education(person_id);

-- ---------- person_identity_documents ----------
CREATE TABLE person_identity_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id         UUID         NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    document_kind     VARCHAR(30)  NOT NULL
                      CHECK (document_kind IN ('PAN','AADHAAR','PASSPORT','DRIVING_LICENCE','VOTER_ID','OTHER')),
    document_number   VARCHAR(60)  NOT NULL,
    issued_by         VARCHAR(255),
    issued_on         DATE,
    expires_on        DATE,
    document_url      VARCHAR(500),
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    version           BIGINT       NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        UUID,
    updated_by        UUID,
    CONSTRAINT ck_person_identity_documents_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE INDEX idx_person_identity_documents_person_kind
    ON person_identity_documents(person_id, document_kind);
-- PAN + Aadhaar are nationally unique in India; enforce across live rows.
CREATE UNIQUE INDEX ux_person_identity_documents_pan
    ON person_identity_documents(document_number)
    WHERE document_kind = 'PAN' AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_person_identity_documents_aadhaar
    ON person_identity_documents(document_number)
    WHERE document_kind = 'AADHAAR' AND deleted_at IS NULL;
CREATE INDEX idx_person_identity_documents_number
    ON person_identity_documents(document_kind, document_number)
    WHERE deleted_at IS NULL;

-- ---------- person_duplicate_rules ----------
-- Configurable per tenant. A rule is enabled + weighted; the DuplicateDetectionService
-- runs every enabled rule and returns the union of matches ordered by highest weight.
CREATE TABLE person_duplicate_rules (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    rule_kind      VARCHAR(30)  NOT NULL
                   CHECK (rule_kind IN ('PAN','AADHAAR','PASSPORT','MOBILE','EMAIL','NAME_DOB')),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    weight         INT          NOT NULL DEFAULT 50,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by     UUID,
    updated_by     UUID
);
CREATE UNIQUE INDEX ux_person_duplicate_rules_tenant_kind
    ON person_duplicate_rules(tenant_id, rule_kind);

-- Seed default duplicate rules for the DEFAULT tenant.
INSERT INTO person_duplicate_rules (tenant_id, rule_kind, enabled, weight)
SELECT t.id, k.kind, TRUE, k.weight
FROM tenants t
CROSS JOIN (VALUES
    ('PAN', 100),
    ('AADHAAR', 100),
    ('PASSPORT', 90),
    ('MOBILE', 70),
    ('EMAIL', 70),
    ('NAME_DOB', 50)
) AS k(kind, weight)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

-- ---------- Permissions ----------
INSERT INTO permissions (code, description) VALUES
    ('PERSON_READ',              'Read person profiles'),
    ('PERSON_WRITE',             'Create and modify person profiles'),
    ('PERSON_DELETE',            'Soft-delete person profiles'),
    ('PERSON_DUPLICATE_OVERRIDE','Override duplicate-detection warnings')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'SYSTEM_ADMIN'
  AND p.code IN ('PERSON_READ','PERSON_WRITE','PERSON_DELETE','PERSON_DUPLICATE_OVERRIDE')
ON CONFLICT DO NOTHING;
