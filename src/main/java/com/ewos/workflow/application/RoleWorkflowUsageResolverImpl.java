package com.ewos.workflow.application;

import com.ewos.identity.application.RoleWorkflowUsageResolver;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The workflow module's implementation of identity's {@link RoleWorkflowUsageResolver} port. */
@Component
@Transactional(readOnly = true)
public class RoleWorkflowUsageResolverImpl implements RoleWorkflowUsageResolver {

    private static final List<WorkflowTaskStatus> PENDING_STATUSES =
            List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED);

    private final WorkflowTaskRepository tasks;

    public RoleWorkflowUsageResolverImpl(WorkflowTaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    public int countPendingTasksForRole(UUID tenantId, String roleName) {
        return tasks.findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(
                        tenantId, roleName, PENDING_STATUSES)
                .size();
    }
}
