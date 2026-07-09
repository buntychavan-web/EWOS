-- Sprint 4: user management extensions.
-- Adds created_by / updated_by auditor columns to existing tables so any
-- entity extending AuditableEntity gets end-to-end auditing, plus the
-- password_history and login_history tables.

ALTER TABLE users
    ADD COLUMN created_by          UUID,
    ADD COLUMN updated_by          UUID,
    ADD COLUMN password_changed_at TIMESTAMPTZ;

ALTER TABLE roles
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

ALTER TABLE permissions
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

ALTER TABLE refresh_tokens
    ADD COLUMN created_by UUID,
    ADD COLUMN updated_by UUID;

CREATE TABLE password_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by    UUID,
    updated_by    UUID
);

CREATE INDEX idx_password_history_user_created
    ON password_history(user_id, created_at DESC);

CREATE TABLE login_history (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         REFERENCES users(id) ON DELETE SET NULL,
    attempted_username VARCHAR(150) NOT NULL,
    ip_address         VARCHAR(45),
    user_agent         VARCHAR(500),
    success            BOOLEAN      NOT NULL,
    failure_reason     VARCHAR(200),
    occurred_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         UUID,
    updated_by         UUID
);

CREATE INDEX idx_login_history_user_occurred ON login_history(user_id, occurred_at DESC);
CREATE INDEX idx_login_history_occurred      ON login_history(occurred_at DESC);
