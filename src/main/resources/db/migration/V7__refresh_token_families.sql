-- WP-001 hardening: refresh-token family + device metadata.
--
-- Design-only: the columns exist so device-based revocation is a schema-safe
-- extension. The Sprint 2 auth service still writes one token per login, and
-- `family_id` defaults to the token's own id — a family of one. When device
-- claims land on the JWT (later WP), issuance will populate `device_label`
-- and reuse an existing `family_id` on rotation. Nothing here changes today's
-- refresh flow.

ALTER TABLE refresh_tokens
    ADD COLUMN family_id UUID;

ALTER TABLE refresh_tokens
    ADD COLUMN device_label VARCHAR(255);

ALTER TABLE refresh_tokens
    ADD COLUMN revoked_reason VARCHAR(60);

-- Existing rows: back-fill family_id = id so the "revoke by family" query is
-- meaningful even for pre-migration tokens. New rows default to their own id
-- via application code (see AuthenticationService).
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user_active
    ON refresh_tokens(user_id) WHERE revoked = FALSE;
