-- Sprint 21 UAT — a brand-new tenant has no way to get a leave request approved out of the box.
--
-- LeaveSelfService.submitMyRequest() / LeaveRequestController.submit() both resolve an ACTIVE
-- WorkflowDefinition for subjectType = 'leave.request' (LeaveRequestService.SUBJECT_TYPE) via
-- WorkflowDefinitionService.findEffective() — and 404/409 with "No active workflow definition is
-- configured ... contact an administrator" when none exists. No such definition was ever seeded
-- for any tenant, including the bootstrap tenant used throughout this environment, so the entire
-- Leave module's submit path was unusable without an admin hand-building a full workflow
-- definition (states + transitions) through the generic Workflow API first.
--
-- Mirrors V36__client_approval_workflows.sql's exact pattern: one ordinary WorkflowDefinition row
-- (data, not code) using the existing engine as-is. The actual approve/reject authorization is
-- direct RBAC in LeaveRequestService (manager-of-employee or LEAVE_ADMIN/LEAVE_APPROVE), not
-- workflow task assignment — the instance this starts is an audit-trail/state marker, exactly the
-- same relationship PAYROLL_CLIENT_APPROVAL already has to a payroll run.
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0005-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'LEAVE_APPROVAL',
    'Leave Request Approval',
    'Tracks a leave request from submission through manager/admin decision.',
    'leave.request',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0005-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0005-000000000003', '00000000-0000-0000-0005-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20),
    ('00000000-0000-0000-0005-000000000004', '00000000-0000-0000-0005-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0005-000000000005', '00000000-0000-0000-0005-000000000001',
     '00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0005-000000000003',
     'APPROVE', 'LEAVE_APPROVE', FALSE),
    ('00000000-0000-0000-0005-000000000006', '00000000-0000-0000-0005-000000000001',
     '00000000-0000-0000-0005-000000000002', '00000000-0000-0000-0005-000000000004',
     'REJECT', 'LEAVE_APPROVE', FALSE)
ON CONFLICT DO NOTHING;
