-- Sprint 27B — ESS/MSS unified approvals inbox (PRD §16). No existing table anywhere in EWOS
-- records idempotency keys (verified by repository search for "Idempotency" before writing this
-- migration) — the platform's other write endpoints simply have no such requirement documented.
-- Sprint 27B is the first to explicitly require "Idempotency-Key support on all new POST
-- endpoints" (individual approve/reject act-through, and the bulk-act endpoint), so this is
-- genuinely new schema, not a duplicate of something reusable.
--
-- The unique constraint is the concurrency guard: two requests racing on the same
-- (tenant, actor, endpoint, key) tuple can only have one INSERT succeed — see
-- com.ewos.shared.idempotency.IdempotencyService for how the loser replays the winner's stored
-- response (or gets a 409 if the winner hasn't finished yet).
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    endpoint VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- IdempotencyKey extends AuditableEntity, which maps these two columns on every entity.
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_idempotency_key UNIQUE (tenant_id, actor_user_id, endpoint, idempotency_key)
);

CREATE INDEX idx_idempotency_keys_created_at ON idempotency_keys (created_at);
