-- Learning & Development (T9 — Volume 5)
--
-- Course catalogue, learning paths, training sessions, enrollments, attendance, assessments, and
-- certifications with expiry tracking.

CREATE TABLE training_courses (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    delivery_mode         VARCHAR(16) NOT NULL,
    provider              VARCHAR(256),
    duration_hours        NUMERIC(6,2),
    cost                  NUMERIC(14,2),
    currency              VARCHAR(3),
    certification_offered BOOLEAN     NOT NULL DEFAULT FALSE,
    certification_valid_days INT,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_training_courses_mode
        CHECK (delivery_mode IN ('INTERNAL','EXTERNAL','ONLINE','HYBRID','ON_THE_JOB'))
);

CREATE UNIQUE INDEX ux_training_courses_code_alive
    ON training_courses (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE learning_paths (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    code                  VARCHAR(64) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    description           VARCHAR(4000),
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_learning_paths_code_alive
    ON learning_paths (tenant_id, company_id, LOWER(code))
    WHERE deleted_at IS NULL;

CREATE TABLE learning_path_courses (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    path_id               UUID        NOT NULL REFERENCES learning_paths (id) ON DELETE CASCADE,
    course_id             UUID        NOT NULL REFERENCES training_courses (id),
    display_order         INT         NOT NULL DEFAULT 0,
    mandatory             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX ix_learning_path_courses_path
    ON learning_path_courses (tenant_id, path_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TABLE training_sessions (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    course_id             UUID        NOT NULL REFERENCES training_courses (id),
    name                  VARCHAR(256) NOT NULL,
    starts_at             TIMESTAMPTZ NOT NULL,
    ends_at               TIMESTAMPTZ NOT NULL,
    venue                 VARCHAR(512),
    trainer_name          VARCHAR(256),
    trainer_employee_id   UUID,
    capacity              INT,
    status                VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    notes                 VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_training_sessions_status
        CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT ck_training_sessions_dates CHECK (ends_at > starts_at)
);

CREATE INDEX ix_training_sessions_course_time
    ON training_sessions (tenant_id, course_id, starts_at)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_training_sessions_status
    ON training_sessions (tenant_id, company_id, status)
    WHERE deleted_at IS NULL;

CREATE TABLE training_enrollments (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    course_id             UUID        NOT NULL REFERENCES training_courses (id),
    session_id            UUID        REFERENCES training_sessions (id),
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    learning_path_id      UUID        REFERENCES learning_paths (id),
    nominated_by          UUID,
    nominated_at          TIMESTAMPTZ,
    enrolled_at           TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    withdrawn_at          TIMESTAMPTZ,
    withdrawal_reason     VARCHAR(2000),
    attendance_percent    NUMERIC(5,2),
    assessment_score      NUMERIC(5,2),
    passed                BOOLEAN,
    status                VARCHAR(32) NOT NULL DEFAULT 'NOMINATED',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_training_enrollments_status
        CHECK (status IN
            ('NOMINATED','ENROLLED','IN_PROGRESS','COMPLETED','WITHDRAWN','NO_SHOW','FAILED')),
    CONSTRAINT ck_training_enrollments_attendance
        CHECK (attendance_percent IS NULL
               OR (attendance_percent >= 0 AND attendance_percent <= 100))
);

CREATE INDEX ix_training_enrollments_employee
    ON training_enrollments (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_training_enrollments_course_status
    ON training_enrollments (tenant_id, course_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_training_enrollments_session
    ON training_enrollments (tenant_id, session_id)
    WHERE deleted_at IS NULL AND session_id IS NOT NULL;

CREATE TABLE certifications (
    id                    UUID        PRIMARY KEY,
    tenant_id             UUID        NOT NULL,
    company_id            UUID        NOT NULL,
    employee_id           UUID        NOT NULL REFERENCES employees (id),
    course_id             UUID        REFERENCES training_courses (id),
    enrollment_id         UUID        REFERENCES training_enrollments (id),
    certification_name    VARCHAR(256) NOT NULL,
    issuing_body          VARCHAR(256),
    reference_number      VARCHAR(128),
    issued_at             DATE        NOT NULL,
    expires_at            DATE,
    status                VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    certificate_uri       VARCHAR(1024),
    revoked_at            TIMESTAMPTZ,
    revocation_reason     VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    version_no            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_certifications_status
        CHECK (status IN ('ACTIVE','EXPIRED','REVOKED'))
);

CREATE INDEX ix_certifications_employee
    ON certifications (tenant_id, employee_id)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_certifications_expiry
    ON certifications (tenant_id, company_id, expires_at)
    WHERE deleted_at IS NULL AND expires_at IS NOT NULL AND status = 'ACTIVE';

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'LEARNING_READ',       'View catalogue, paths, sessions, enrollments'),
    (gen_random_uuid(), 'LEARNING_WRITE',      'Create + edit catalogue, paths, sessions'),
    (gen_random_uuid(), 'LEARNING_NOMINATE',   'Nominate employees for training'),
    (gen_random_uuid(), 'LEARNING_ENROLL',     'Enroll + withdraw employees'),
    (gen_random_uuid(), 'LEARNING_ATTEND',     'Record attendance + assessment'),
    (gen_random_uuid(), 'LEARNING_CERTIFY',    'Issue + revoke certifications'),
    (gen_random_uuid(), 'LEARNING_ADMIN',      'Administer learning module');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN
    ('LEARNING_READ','LEARNING_WRITE','LEARNING_NOMINATE','LEARNING_ENROLL',
     'LEARNING_ATTEND','LEARNING_CERTIFY','LEARNING_ADMIN')
WHERE r.name = 'SYSTEM_ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
