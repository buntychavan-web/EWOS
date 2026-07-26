package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleWorkflowUsageResolverImplTest {

    @Mock WorkflowTaskRepository tasks;

    private RoleWorkflowUsageResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new RoleWorkflowUsageResolverImpl(tasks);
    }

    @Test
    void countsOnlyOpenAndClaimedTasksRoutedToTheRoleName() {
        UUID tenantId = UUID.randomUUID();
        when(tasks.findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(
                        eq(tenantId),
                        eq("Payroll Reviewer"),
                        eq(List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED))))
                .thenReturn(List.of(new WorkflowTask(), new WorkflowTask()));

        assertThat(resolver.countPendingTasksForRole(tenantId, "Payroll Reviewer")).isEqualTo(2);
    }

    @Test
    void zeroWhenNoTasksRouted() {
        UUID tenantId = UUID.randomUUID();
        when(tasks.findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(resolver.countPendingTasksForRole(tenantId, "Unused Role")).isZero();
    }
}
