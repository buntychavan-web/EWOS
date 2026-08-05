-- Sprint 26 — Exit Management V1, increment 1.
--
-- Resignation.resignationType classifies how a separation was initiated. Before this migration,
-- every row implicitly meant "an employee submitted this" even though the only submission path
-- was actually EXIT_WRITE-gated (HR/admin acting on the employee's behalf) — there was no genuine
-- employee self-service path and no way to distinguish HR-initiated, manager-initiated,
-- retirement, termination, death, or absconding cases from an ordinary resignation. Defaulted to
-- 'SELF_RESIGNATION' for backward compatibility with any existing rows; the application layer
-- always sets it explicitly going forward.
ALTER TABLE resignations
    ADD COLUMN resignation_type VARCHAR(32) NOT NULL DEFAULT 'SELF_RESIGNATION';

ALTER TABLE resignations
    ADD CONSTRAINT ck_resignation_type
        CHECK (resignation_type IN
            ('SELF_RESIGNATION','HR_INITIATED','MANAGER_SEPARATION','RETIREMENT',
             'TERMINATION','DEATH','ABSCONDING'));
