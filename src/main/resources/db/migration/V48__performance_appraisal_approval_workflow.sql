-- Sprint 24A — verified in the Sprint 24 gap analysis: AppraisalService.submitForApproval()
-- (SUBJECT_TYPE = 'performance.appraisal') calls WorkflowInstanceService.start() with a
-- caller-supplied workflowDefinitionId, but no such definition was ever seeded for any tenant. A
-- calibrated appraisal can never be approved or finalised without an admin hand-building a
-- workflow definition through the generic Workflow API first. Same defect class as
-- V43/V44/V45/V46.
--
-- Mirrors V46__offer_approval_workflow.sql's exact pattern. Appraisal approve/reject
-- authorization is direct RBAC in AppraisalService (PERF_APPROVE, checked at the controller via
-- @PreAuthorize) — the instance this starts is an audit-trail/state marker, not a
-- task-assignment gate.
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0009-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'PERFORMANCE_APPRAISAL_APPROVAL',
    'Appraisal Approval',
    'Tracks a calibrated appraisal from submission through approver decision.',
    'performance.appraisal',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0009-000000000002', '00000000-0000-0000-0009-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0009-000000000003', '00000000-0000-0000-0009-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20),
    ('00000000-0000-0000-0009-000000000004', '00000000-0000-0000-0009-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0009-000000000005', '00000000-0000-0000-0009-000000000001',
     '00000000-0000-0000-0009-000000000002', '00000000-0000-0000-0009-000000000003',
     'APPROVE', 'PERF_APPROVE', FALSE),
    ('00000000-0000-0000-0009-000000000006', '00000000-0000-0000-0009-000000000001',
     '00000000-0000-0000-0009-000000000002', '00000000-0000-0000-0009-000000000004',
     'REJECT', 'PERF_APPROVE', FALSE)
ON CONFLICT DO NOTHING;
