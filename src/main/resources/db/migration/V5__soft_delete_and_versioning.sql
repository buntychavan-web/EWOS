-- Sprint 5 hardening: soft delete + optimistic locking + login audit event type.
--
-- Every core identity table gets:
--  * deleted_at TIMESTAMPTZ (null = live, non-null = soft-deleted)
--  * version    BIGINT for JPA @Version-based optimistic locking
--
-- Uniqueness moves from full-column constraints to partial unique indexes so
-- a deleted row keeps its historical (username | email | name | code) without
-- colliding with a freshly-created active row.

-- ---------- users ----------
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
CREATE UNIQUE INDEX ux_users_username_active ON users(username) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_email_active    ON users(email)    WHERE deleted_at IS NULL;

-- ---------- roles ----------
ALTER TABLE roles ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE roles ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_name_key;
CREATE UNIQUE INDEX ux_roles_name_active ON roles(name) WHERE deleted_at IS NULL;

-- ---------- permissions ----------
ALTER TABLE permissions ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE permissions ADD COLUMN version    BIGINT      NOT NULL DEFAULT 0;

ALTER TABLE permissions DROP CONSTRAINT IF EXISTS permissions_code_key;
CREATE UNIQUE INDEX ux_permissions_code_active ON permissions(code) WHERE deleted_at IS NULL;

-- ---------- login_history: discriminate event type ----------
-- Adds an event_type column so we can now track LOGOUT / REFRESH_* alongside login attempts.
ALTER TABLE login_history ADD COLUMN event_type VARCHAR(30) NOT NULL DEFAULT 'LOGIN_ATTEMPT';

UPDATE login_history
SET event_type = CASE
    WHEN success THEN 'LOGIN_SUCCESS'
    ELSE 'LOGIN_FAILURE'
END
WHERE event_type = 'LOGIN_ATTEMPT';

CREATE INDEX idx_login_history_event_type_occurred ON login_history(event_type, occurred_at DESC);
