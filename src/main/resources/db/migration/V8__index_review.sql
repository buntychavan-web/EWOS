-- WP-002A: Database indexing review — fill the gaps identified by inspecting
-- every hot-path query in the codebase against the existing indexes.
--
-- New indexes below are all created with IF NOT EXISTS so the migration is
-- safe to apply to a database that partially migrated in a prior run.

-- login_history: forensic queries by (attempted_username, occurred_at DESC)
-- (e.g. "was this login ever attempted?", "brute-force detection by username").
-- We already index (user_id, occurred_at) and (occurred_at) but not username.
CREATE INDEX IF NOT EXISTS idx_login_history_username_occurred
    ON login_history(LOWER(attempted_username), occurred_at DESC);

-- login_history: report queries filtered by success flag ("failed logins in
-- the last 24h") benefit from a partial index.
CREATE INDEX IF NOT EXISTS idx_login_history_failures
    ON login_history(occurred_at DESC) WHERE success = FALSE;

-- users: case-insensitive lookups on email happen in reset-password + admin
-- flows. Existing partial UNIQUE is on LOWER(email); this ships an index on
-- LOWER(username) too so lookup-by-username is B-tree-served.
CREATE INDEX IF NOT EXISTS idx_users_username_lower
    ON users(LOWER(username)) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_users_email_lower
    ON users(LOWER(email)) WHERE deleted_at IS NULL;

-- users: administrative "who's currently locked" queries key on locked_until.
-- V6 already added a partial index; this is the sibling on failed_login_attempts
-- for "who's close to being locked" alerts.
CREATE INDEX IF NOT EXISTS idx_users_failed_attempts
    ON users(failed_login_attempts) WHERE failed_login_attempts > 0;

-- refresh_tokens: expiration sweeps (see PurgeJob) filter by expires_at.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at) WHERE revoked = FALSE;

-- password_history: `existsByUserAndPasswordHash`-style checks are already
-- indexed by (user_id, created_at) from V4. Add the hash lookup so
-- "was this password ever used?" is not a scan.
CREATE INDEX IF NOT EXISTS idx_password_history_user_hash
    ON password_history(user_id, password_hash);
