-- WP-001 hardening: account lockout support.
--
-- Adds two nullable / defaulted columns to the users table so failed-login
-- tracking can survive restarts and be inspected in the DB. Rate limiting
-- itself lives in-process (see AuthRateLimitFilter); this migration only
-- persists the per-user lockout state.

ALTER TABLE users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD COLUMN locked_until TIMESTAMPTZ;

CREATE INDEX idx_users_locked_until ON users(locked_until) WHERE locked_until IS NOT NULL;
