-- Sprint 22A — verified in the Sprint 22 gap analysis: JobRequisitionService.submit() (SUBJECT_TYPE
-- = 'recruitment.requisition') calls WorkflowInstanceService.start() with a caller-supplied
-- workflowDefinitionId, but no such definition was ever seeded for any tenant. A requisition can be
-- created and drafted but never legally submitted for approval — there is no valid entry point into
-- the hiring pipeline without an admin hand-building a workflow definition through the generic
-- Workflow API first.
--
-- Mirrors V43__leave_approval_workflow.sql's exact pattern: one ordinary WorkflowDefinition row
-- (data, not code) using the existing engine as-is. Requisition approve/reject authorization is
-- direct RBAC in JobRequisitionService (RECRUITMENT_APPROVE, checked at the controller via
-- @PreAuthorize) — the instance this starts is an audit-trail/state marker, not a task-assignment
-- gate, exactly the same relationship LEAVE_APPROVAL already has to a leave request.
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0007-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'RECRUITMENT_REQUISITION_APPROVAL',
    'Job Requisition Approval',
    'Tracks a job requisition from submission through admin/approver decision.',
    'recruitment.requisition',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0007-000000000002', '00000000-0000-0000-0007-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0007-000000000003', '00000000-0000-0000-0007-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20),
    ('00000000-0000-0000-0007-000000000004', '00000000-0000-0000-0007-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0007-000000000005', '00000000-0000-0000-0007-000000000001',
     '00000000-0000-0000-0007-000000000002', '00000000-0000-0000-0007-000000000003',
     'APPROVE', 'RECRUITMENT_APPROVE', FALSE),
    ('00000000-0000-0000-0007-000000000006', '00000000-0000-0000-0007-000000000001',
     '00000000-0000-0000-0007-000000000002', '00000000-0000-0000-0007-000000000004',
     'REJECT', 'RECRUITMENT_APPROVE', FALSE)
ON CONFLICT DO NOTHING;
