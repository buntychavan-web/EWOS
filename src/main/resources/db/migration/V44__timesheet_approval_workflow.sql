-- Sprint 21 UAT — same gap as V43, this time for Attendance: TimesheetService.submit() resolves
-- a WorkflowDefinition (subjectType = 'attendance.timesheet', TimesheetService.SUBJECT_TYPE) via
-- the caller-supplied workflowDefinitionId, which the frontend/admin has to look up from
-- GET /api/v1/workflow/definitions first — but with zero definitions seeded for that subject type
-- on any tenant, there was nothing to look up, and every timesheet submission failed at the
-- workflow engine's own validation (unknown/inactive definition id) before ever reaching
-- TimesheetService.approve()/reject(), which — exactly like Leave — gate purely on the ATT_APPROVE
-- authority, not on workflow task assignment. The instance this starts is an audit-trail/state
-- marker only.
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0006-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'TIMESHEET_APPROVAL',
    'Timesheet Approval',
    'Tracks a timesheet from submission through manager/admin decision.',
    'attendance.timesheet',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0006-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0006-000000000003', '00000000-0000-0000-0006-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20),
    ('00000000-0000-0000-0006-000000000004', '00000000-0000-0000-0006-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0006-000000000005', '00000000-0000-0000-0006-000000000001',
     '00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0006-000000000003',
     'APPROVE', 'ATT_APPROVE', FALSE),
    ('00000000-0000-0000-0006-000000000006', '00000000-0000-0000-0006-000000000001',
     '00000000-0000-0000-0006-000000000002', '00000000-0000-0000-0006-000000000004',
     'REJECT', 'ATT_APPROVE', FALSE)
ON CONFLICT DO NOTHING;
