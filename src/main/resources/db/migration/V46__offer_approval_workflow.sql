-- Sprint 22A — verified in the Sprint 22 gap analysis: OfferService.submitForApproval()
-- (SUBJECT_TYPE = 'offer.approval') calls WorkflowInstanceService.start() with a caller-supplied
-- workflowDefinitionId, but no such definition was ever seeded for any tenant. An offer can be
-- drafted but never routed for internal approval — the module cannot get a single candidate to an
-- accepted offer without an admin hand-building a workflow definition through the generic Workflow
-- API first. Same defect class as V43/V44/V45.
--
-- Mirrors V45__recruitment_requisition_approval_workflow.sql's exact pattern. Offer approve/reject
-- authorization is direct RBAC in OfferService (OFFER_APPROVE, checked at the controller via
-- @PreAuthorize) — the instance this starts is an audit-trail/state marker, not a task-assignment
-- gate.
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0008-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'OFFER_APPROVAL',
    'Offer Approval',
    'Tracks an offer from submission through admin/approver decision.',
    'offer.approval',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0008-000000000003', '00000000-0000-0000-0008-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20),
    ('00000000-0000-0000-0008-000000000004', '00000000-0000-0000-0008-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0008-000000000005', '00000000-0000-0000-0008-000000000001',
     '00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000003',
     'APPROVE', 'OFFER_APPROVE', FALSE),
    ('00000000-0000-0000-0008-000000000006', '00000000-0000-0000-0008-000000000001',
     '00000000-0000-0000-0008-000000000002', '00000000-0000-0000-0008-000000000004',
     'REJECT', 'OFFER_APPROVE', FALSE)
ON CONFLICT DO NOTHING;
