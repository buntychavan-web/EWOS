-- Sprint 26 — Exit Management V1, increment 7.
--
-- Closes the remaining gap in "Knowledge Transfer: KT tasks, successor assignment, document
-- handover, client handover" (item 7): item_type classifies a KT item as a generic task, a
-- document handover, or a client handover; successor_employee_id designates the one overall
-- successor for the exiting role, independent of the per-item transferred_to routing.
ALTER TABLE resignations ADD COLUMN successor_employee_id UUID REFERENCES employees (id);

ALTER TABLE knowledge_transfer_items
    ADD COLUMN item_type VARCHAR(32) NOT NULL DEFAULT 'TASK';
ALTER TABLE knowledge_transfer_items
    ADD CONSTRAINT ck_kt_item_type CHECK (item_type IN ('TASK','DOCUMENT_HANDOVER','CLIENT_HANDOVER'));
