-- Sprint 22A — verified in the Sprint 22 gap analysis: candidates are highly sensitive personal
-- data under GDPR/CCPA, and nothing in the schema captured consent or a data-retention basis.
--
-- Deliberately minimal and schema-forward rather than a full policy engine: retention_policy_code
-- is a free-standing reference string (not a hardcoded duration, not yet FK'd to a policy table) so
-- a future sprint can introduce a proper retention_policies table and point this column at it
-- without an ALTER. retention_expires_at is a plain timestamp an admin (or, later, an automated job
-- computing it from a policy) can set directly; no erasure workflow is implemented this sprint.
ALTER TABLE candidates
    ADD COLUMN consent_given         BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN consent_given_at      TIMESTAMPTZ,
    ADD COLUMN consent_withdrawn_at  TIMESTAMPTZ,
    ADD COLUMN consent_source        VARCHAR(32),
    ADD COLUMN retention_policy_code VARCHAR(64),
    ADD COLUMN retention_expires_at  TIMESTAMPTZ;

ALTER TABLE candidates
    ADD CONSTRAINT ck_candidates_consent_source
        CHECK (consent_source IS NULL OR consent_source IN (
            'APPLICATION_FORM','MANUAL_ENTRY','REFERRAL','AGENCY','IMPORTED','OTHER'));

ALTER TABLE candidates
    ADD CONSTRAINT ck_candidates_consent_withdrawn_after_given
        CHECK (consent_withdrawn_at IS NULL
           OR (consent_given_at IS NOT NULL AND consent_withdrawn_at >= consent_given_at));

CREATE INDEX ix_candidates_retention_expires
    ON candidates (tenant_id, retention_expires_at)
    WHERE deleted_at IS NULL AND retention_expires_at IS NOT NULL;
