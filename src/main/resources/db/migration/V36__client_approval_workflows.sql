-- Sprint 14.3 — Client Approval Workflow definitions.
--
-- Reuses the existing Workflow Engine (V11) exactly as-is: no new tables, no engine change.
-- These are two ordinary WorkflowDefinition rows (data, not code) so downstream callers drive
-- them entirely through the existing generic Workflow API
-- (POST /api/v1/workflow/instances, /workflow/tasks/...). Seeded under the Sprint 14.1/14.2
-- bootstrap tenant so they're immediately usable in this environment.
--
--   CLIENT_ONBOARDING          subjectType = CLIENT       — onboarding review for a new Client
--   PAYROLL_CLIENT_APPROVAL    subjectType = PAYROLL_RUN  — client sign-off before a payroll run
--                                                            is finalized (Sprint 14.3 §3 wires a
--                                                            listener that starts an instance of
--                                                            this definition when a run completes,
--                                                            and finalizes the run automatically
--                                                            once it reaches APPROVED)
--
-- Both definitions share the same 3-state shape: PENDING_REVIEW (initial) -> APPROVED/ACTIVE or
-- REJECTED (terminal), gated by the CLIENT_ADMIN authority already established in Sprint 14.1's
-- Chinese Wall (com.ewos.tenancy.application.ClientAccessGuard#hasUnrestrictedAccess).

-- =====================================================================
-- CLIENT_ONBOARDING
-- =====================================================================
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0003-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'CLIENT_ONBOARDING',
    'Client Onboarding',
    'Review and approve a newly onboarded Client before it becomes active.',
    'CLIENT',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order)
VALUES
    ('00000000-0000-0000-0003-000000000002', '00000000-0000-0000-0003-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10),
    ('00000000-0000-0000-0003-000000000003', '00000000-0000-0000-0003-000000000001', 'ACTIVE',         'Active',         FALSE, TRUE,  20),
    ('00000000-0000-0000-0003-000000000004', '00000000-0000-0000-0003-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0003-000000000005', '00000000-0000-0000-0003-000000000001',
     '00000000-0000-0000-0003-000000000002', '00000000-0000-0000-0003-000000000003',
     'APPROVE', 'CLIENT_ADMIN', FALSE),
    ('00000000-0000-0000-0003-000000000006', '00000000-0000-0000-0003-000000000001',
     '00000000-0000-0000-0003-000000000002', '00000000-0000-0000-0003-000000000004',
     'REJECT', 'CLIENT_ADMIN', FALSE)
ON CONFLICT DO NOTHING;

-- =====================================================================
-- PAYROLL_CLIENT_APPROVAL
-- =====================================================================
INSERT INTO workflow_definitions (id, tenant_id, code, name, description, subject_type, definition_version, active)
VALUES (
    '00000000-0000-0000-0004-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'PAYROLL_CLIENT_APPROVAL',
    'Payroll Client Approval',
    'Client sign-off on a completed payroll run before it is finalized.',
    'PAYROLL_RUN',
    1,
    TRUE
)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_states (id, definition_id, code, name, is_initial, is_terminal, sort_order, sla_hours)
VALUES
    ('00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0004-000000000001', 'PENDING_REVIEW', 'Pending Review', TRUE,  FALSE, 10, 48),
    ('00000000-0000-0000-0004-000000000003', '00000000-0000-0000-0004-000000000001', 'APPROVED',       'Approved',       FALSE, TRUE,  20, NULL),
    ('00000000-0000-0000-0004-000000000004', '00000000-0000-0000-0004-000000000001', 'REJECTED',       'Rejected',       FALSE, TRUE,  30, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO workflow_transitions (id, definition_id, from_state_id, to_state_id, action_code, required_role, auto)
VALUES
    ('00000000-0000-0000-0004-000000000005', '00000000-0000-0000-0004-000000000001',
     '00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0004-000000000003',
     'APPROVE', 'CLIENT_ADMIN', FALSE),
    ('00000000-0000-0000-0004-000000000006', '00000000-0000-0000-0004-000000000001',
     '00000000-0000-0000-0004-000000000002', '00000000-0000-0000-0004-000000000004',
     'REJECT', 'CLIENT_ADMIN', FALSE)
ON CONFLICT DO NOTHING;
