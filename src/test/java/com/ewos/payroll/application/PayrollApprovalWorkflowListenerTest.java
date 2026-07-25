package com.ewos.payroll.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.application.WorkflowTaskService;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowDefinitionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollApprovalWorkflowListenerTest {

    @Mock WorkflowDefinitionRepository definitions;
    @Mock WorkflowInstanceService workflowInstances;
    @Mock WorkflowTaskService workflowTasks;
    @Mock PayrollRunService runs;

    private PayrollApprovalWorkflowListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new PayrollApprovalWorkflowListener(
                        definitions, workflowInstances, workflowTasks, runs);
    }

    private static WorkflowInstanceResponse instanceResponse(UUID id) {
        return new WorkflowInstanceResponse(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PAYROLL_CLIENT_APPROVAL",
                1,
                "PAYROLL_RUN",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "PENDING_REVIEW",
                WorkflowInstanceStatus.RUNNING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L);
    }

    private static PayrollEvent payrollEvent(
            PayrollEventType type, UUID tenantId, UUID companyId, UUID runId) {
        return new PayrollEvent(
                type, tenantId, companyId, null, null, runId, null, null, null, null, null);
    }

    private static WorkflowEvent workflowEvent(
            WorkflowEventType type,
            UUID tenantId,
            String subjectType,
            UUID subjectId,
            String toState) {
        return new WorkflowEvent(
                type,
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                subjectType,
                subjectId,
                "PENDING_REVIEW",
                toState,
                "APPROVE",
                null,
                UUID.randomUUID(),
                null);
    }

    @Test
    void runCompletedStartsWorkflowInstanceAgainstTheRun() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        when(definitions.findActiveByTenantAndCode(tenantId, "PAYROLL_CLIENT_APPROVAL"))
                .thenReturn(List.of(def));
        UUID instanceId = UUID.randomUUID();
        when(workflowInstances.start(any())).thenReturn(instanceResponse(instanceId));

        listener.onPayrollEvent(
                payrollEvent(PayrollEventType.RUN_COMPLETED, tenantId, companyId, runId));

        ArgumentCaptor<StartInstanceRequest> captor =
                ArgumentCaptor.forClass(StartInstanceRequest.class);
        verify(workflowInstances).start(captor.capture());
        StartInstanceRequest req = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(req.tenantId()).isEqualTo(tenantId);
        org.assertj.core.api.Assertions.assertThat(req.companyId()).isEqualTo(companyId);
        org.assertj.core.api.Assertions.assertThat(req.definitionId()).isEqualTo(def.getId());
        org.assertj.core.api.Assertions.assertThat(req.subjectType()).isEqualTo("PAYROLL_RUN");
        org.assertj.core.api.Assertions.assertThat(req.subjectId()).isEqualTo(runId);

        ArgumentCaptor<AssignTaskRequest> taskCaptor =
                ArgumentCaptor.forClass(AssignTaskRequest.class);
        verify(workflowTasks)
                .assign(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(instanceId),
                        taskCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(taskCaptor.getValue().assigneeActorType())
                .isEqualTo(WorkflowActorType.ROLE);
        org.assertj.core.api.Assertions.assertThat(taskCaptor.getValue().assigneeRoleCode())
                .isEqualTo("CLIENT_ADMIN");
    }

    @Test
    void ignoresNonRunCompletedEvents() {
        listener.onPayrollEvent(
                payrollEvent(
                        PayrollEventType.RUN_STARTED,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()));
        org.mockito.Mockito.verifyNoInteractions(definitions, workflowInstances);
    }

    @Test
    void skipsWhenNoActiveDefinitionConfigured() {
        UUID tenantId = UUID.randomUUID();
        when(definitions.findActiveByTenantAndCode(tenantId, "PAYROLL_CLIENT_APPROVAL"))
                .thenReturn(List.of());

        listener.onPayrollEvent(
                payrollEvent(
                        PayrollEventType.RUN_COMPLETED,
                        tenantId,
                        UUID.randomUUID(),
                        UUID.randomUUID()));

        org.mockito.Mockito.verifyNoInteractions(workflowInstances);
    }

    @Test
    void swallowsExceptionFromWorkflowStart() {
        UUID tenantId = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        when(definitions.findActiveByTenantAndCode(tenantId, "PAYROLL_CLIENT_APPROVAL"))
                .thenReturn(List.of(def));
        doThrow(new RuntimeException("boom")).when(workflowInstances).start(any());

        org.assertj.core.api.Assertions.assertThatCode(
                        () ->
                                listener.onPayrollEvent(
                                        payrollEvent(
                                                PayrollEventType.RUN_COMPLETED,
                                                tenantId,
                                                UUID.randomUUID(),
                                                UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void approvedWorkflowCompletionFinalizesTheRun() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        listener.onWorkflowEvent(
                workflowEvent(
                        WorkflowEventType.INSTANCE_COMPLETED,
                        tenantId,
                        "PAYROLL_RUN",
                        runId,
                        "APPROVED"));

        verify(runs).finalizeRun(tenantId, runId);
    }

    @Test
    void rejectedWorkflowCompletionDoesNotFinalize() {
        listener.onWorkflowEvent(
                workflowEvent(
                        WorkflowEventType.INSTANCE_COMPLETED,
                        UUID.randomUUID(),
                        "PAYROLL_RUN",
                        UUID.randomUUID(),
                        "REJECTED"));

        verify(runs, never()).finalizeRun(any(), any());
    }

    @Test
    void ignoresCompletionForOtherSubjectTypes() {
        listener.onWorkflowEvent(
                workflowEvent(
                        WorkflowEventType.INSTANCE_COMPLETED,
                        UUID.randomUUID(),
                        "CLIENT",
                        UUID.randomUUID(),
                        "ACTIVE"));

        verify(runs, never()).finalizeRun(any(), any());
    }

    @Test
    void swallowsExceptionFromFinalizeRun() {
        UUID tenantId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        doThrow(new RuntimeException("boom")).when(runs).finalizeRun(tenantId, runId);

        org.assertj.core.api.Assertions.assertThatCode(
                        () ->
                                listener.onWorkflowEvent(
                                        workflowEvent(
                                                WorkflowEventType.INSTANCE_COMPLETED,
                                                tenantId,
                                                "PAYROLL_RUN",
                                                runId,
                                                "APPROVED")))
                .doesNotThrowAnyException();
    }
}
