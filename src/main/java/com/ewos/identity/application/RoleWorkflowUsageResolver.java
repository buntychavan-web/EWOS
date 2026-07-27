package com.ewos.identity.application;

import java.util.UUID;

/**
 * Sprint 1.4 Role Usage Impact Analysis (Product Owner addition) — counts pending workflow tasks
 * routed to a role by name via {@code WorkflowTask.assigneeRoleCode} / {@code
 * WorkflowActorType.ROLE}, a real, already-existing role-based task-assignment mechanism (not
 * simulated for this report). Mirrors {@link TenantClaimResolver}/{@link EmployeeClaimResolver}'s
 * dependency-inversion shape: defined here so {@code com.ewos.identity} stays free of a
 * compile-time dependency on {@code com.ewos.workflow}; {@code com.ewos.workflow} provides the one
 * implementation.
 */
public interface RoleWorkflowUsageResolver {

    /**
     * Roles are matched to tasks by name, not id — see the Sprint 1.4 SDD §6.2 for the role-rename
     * risk this implies.
     */
    int countPendingTasksForRole(UUID tenantId, String roleName);
}
