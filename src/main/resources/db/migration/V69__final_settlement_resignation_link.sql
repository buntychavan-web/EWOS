-- Sprint 26 — Exit Management V1, increment 5.
--
-- Links a Full & Final settlement back to the resignation that triggered it, so Exit can surface
-- F&F progress without duplicating any Payroll settlement logic — the settlement itself is still
-- created, approved, and settled entirely through the existing FinalSettlementService/
-- FinalSettlementController (com.ewos.payroll). Nullable: settlements created without going
-- through an exit (e.g. a standalone termination entered directly in Payroll) keep working
-- unchanged.
ALTER TABLE final_settlements ADD COLUMN resignation_id UUID REFERENCES resignations (id);

CREATE INDEX idx_final_settlements_resignation
    ON final_settlements (tenant_id, resignation_id)
    WHERE resignation_id IS NOT NULL AND deleted_at IS NULL;
