package com.ewos.employee.domain;

/**
 * Workflow subject-type identifiers owned by the employee module, for use with {@code
 * com.ewos.workflow}'s generic engine (same string-constant convention as {@code
 * ExitService.WORKFLOW_SUBJECT_TYPE}, {@code "exit.resignation"}).
 *
 * <p>Sprint 27A registers {@link #PROFILE_CHANGE} ahead of its consumer (My Profile self-service,
 * Sprint 27D's sensitive-field change-request flow, PRD FR-4) so that: (1) this constant is fixed
 * and importable now rather than invented ad hoc when 27D lands, and (2) a tenant can configure a
 * {@code WorkflowDefinition} against this subject type before the feature that attaches instances
 * to it exists — mirroring Exit's own "attach an instance only if a tenant has one configured"
 * optional-workflow pattern. No {@code WorkflowInstance} is created against this subject type by
 * any code in Sprint 27A; there is no attach/approve orchestration yet, only the identifier.
 */
public final class EmployeeWorkflowSubjectTypes {

    /** Subject type for a requested change to one or more sensitive {@code Employee} fields. */
    public static final String PROFILE_CHANGE = "employee.profile_change";

    private EmployeeWorkflowSubjectTypes() {}
}
