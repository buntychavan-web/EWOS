package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.CompleteTaskRequest;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowApprovalMode;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class WorkflowTaskServiceTest {

    @Mock WorkflowTaskRepository tasks;
    @Mock WorkflowInstanceService instanceService;
    @Mock ClientAccessGuard guard;
    @Mock ApproverResolver approverResolver;
    @Mock WorkflowDelegationService delegations;
    @Mock ApplicationEventPublisher events;

    private final WorkflowTransitionPolicy policy = new WorkflowTransitionPolicy();
    private WorkflowTaskService service;
    private UUID tenantId;
    private UUID actor;

    @BeforeEach
    void setUp() {
        service =
                new WorkflowTaskService(
                        tasks,
                        instanceService,
                        policy,
                        new WorkflowMapper(),
                        guard,
                        approverResolver,
                        delegations,
                        events);
        tenantId = UUID.randomUUID();
        actor = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(actor.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static WorkflowInstance instance(WorkflowState state) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        instance.setTenantId(UUID.randomUUID());
        instance.setCompanyId(UUID.randomUUID());
        instance.setDefinition(new WorkflowDefinition());
        instance.setCurrentState(state);
        instance.setStatus(WorkflowInstanceStatus.RUNNING);
        return instance;
    }

    private static WorkflowState state(WorkflowApprovalMode mode) {
        WorkflowState s = new WorkflowState();
        s.setId(UUID.randomUUID());
        s.setCode("REVIEW");
        s.setApprovalMode(mode);
        return s;
    }

    private static WorkflowTask task(
            WorkflowInstance instance, WorkflowState state, UUID assignee) {
        WorkflowTask t = new WorkflowTask();
        t.setId(UUID.randomUUID());
        t.setInstance(instance);
        t.setState(state);
        t.setAssigneeActorType(WorkflowActorType.USER);
        t.setAssigneeActorId(assignee);
        t.setStatus(WorkflowTaskStatus.OPEN);
        return t;
    }

    /* ---------------------------- complete(): SINGLE ---------------------------- */

    @Test
    void completeSingleModeAdvancesImmediately() {
        WorkflowState state = state(WorkflowApprovalMode.SINGLE);
        WorkflowInstance instance = instance(state);
        WorkflowTask t = task(instance, state, actor);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(java.util.Optional.of(t));

        service.complete(tenantId, t.getId(), new CompleteTaskRequest("APPROVE", "OK", null));

        verify(instanceService).advance(instance, "APPROVE", actor, t.getId(), null);
        verify(tasks, never()).findAllOfInstanceInStatus(any(), any());
    }

    /* ----------------------------- complete(): ANY ------------------------------ */

    @Test
    void completeAnyModeAdvancesAndCancelsOpenSiblings() {
        WorkflowState state = state(WorkflowApprovalMode.ANY);
        WorkflowInstance instance = instance(state);
        WorkflowTask completing = task(instance, state, actor);
        WorkflowTask sibling = task(instance, state, UUID.randomUUID());
        when(tasks.findByIdAndTenantId(completing.getId(), tenantId))
                .thenReturn(java.util.Optional.of(completing));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(),
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED)))
                .thenReturn(List.of(sibling));

        service.complete(
                tenantId, completing.getId(), new CompleteTaskRequest("APPROVE", null, null));

        assertThat(sibling.getStatus()).isEqualTo(WorkflowTaskStatus.CANCELLED);
        verify(instanceService).advance(instance, "APPROVE", actor, completing.getId(), null);
    }

    /* ----------------------------- complete(): ALL ------------------------------ */

    @Test
    void completeAllModeWaitsWhenSiblingsStillOpen() {
        WorkflowState state = state(WorkflowApprovalMode.ALL);
        WorkflowInstance instance = instance(state);
        WorkflowTask completing = task(instance, state, actor);
        WorkflowTask openSibling = task(instance, state, UUID.randomUUID());
        when(tasks.findByIdAndTenantId(completing.getId(), tenantId))
                .thenReturn(java.util.Optional.of(completing));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(),
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED)))
                .thenReturn(List.of(openSibling));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(), List.of(WorkflowTaskStatus.COMPLETED)))
                .thenReturn(List.of());

        service.complete(
                tenantId, completing.getId(), new CompleteTaskRequest("APPROVE", null, null));

        verify(instanceService, never()).advance(any(), any(), any(), any(), any());
        assertThat(completing.getStatus()).isEqualTo(WorkflowTaskStatus.COMPLETED);
    }

    @Test
    void completeAllModeAdvancesWhenLastMatchingTaskCompletes() {
        WorkflowState state = state(WorkflowApprovalMode.ALL);
        WorkflowInstance instance = instance(state);
        WorkflowTask completing = task(instance, state, actor);
        WorkflowTask alreadyCompleted = task(instance, state, UUID.randomUUID());
        alreadyCompleted.setStatus(WorkflowTaskStatus.COMPLETED);
        alreadyCompleted.setActionCode("APPROVE");
        when(tasks.findByIdAndTenantId(completing.getId(), tenantId))
                .thenReturn(java.util.Optional.of(completing));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(),
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED)))
                .thenReturn(List.of());
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(), List.of(WorkflowTaskStatus.COMPLETED)))
                .thenReturn(List.of(alreadyCompleted));

        service.complete(
                tenantId, completing.getId(), new CompleteTaskRequest("APPROVE", null, null));

        verify(instanceService).advance(instance, "APPROVE", actor, completing.getId(), null);
    }

    @Test
    void completeAllModeFailsFastOnADivergentDecision() {
        WorkflowState state = state(WorkflowApprovalMode.ALL);
        WorkflowInstance instance = instance(state);
        WorkflowTask completing = task(instance, state, actor);
        WorkflowTask alreadyApproved = task(instance, state, UUID.randomUUID());
        alreadyApproved.setStatus(WorkflowTaskStatus.COMPLETED);
        alreadyApproved.setActionCode("APPROVE");
        WorkflowTask stillOpen = task(instance, state, UUID.randomUUID());
        when(tasks.findByIdAndTenantId(completing.getId(), tenantId))
                .thenReturn(java.util.Optional.of(completing));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(),
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED)))
                .thenReturn(List.of(stillOpen));
        when(tasks.findAllOfInstanceInStatus(
                        instance.getId(), List.of(WorkflowTaskStatus.COMPLETED)))
                .thenReturn(List.of(alreadyApproved));

        // This task rejects, diverging from the prior APPROVE decision — fail-fast.
        service.complete(
                tenantId, completing.getId(), new CompleteTaskRequest("REJECT", null, null));

        verify(instanceService).advance(instance, "REJECT", actor, completing.getId(), null);
        assertThat(stillOpen.getStatus()).isEqualTo(WorkflowTaskStatus.CANCELLED);
    }

    /* --------------------------------- claim() ----------------------------------- */

    @Test
    void claimRejectsAnUnrelatedActorWithNoDelegation() {
        WorkflowState state = state(WorkflowApprovalMode.SINGLE);
        WorkflowInstance instance = instance(state);
        UUID assignee = UUID.randomUUID();
        WorkflowTask t = task(instance, state, assignee);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(java.util.Optional.of(t));
        when(delegations.isActiveDelegateOf(tenantId, assignee, actor)).thenReturn(false);

        assertThatThrownBy(() -> service.claim(tenantId, t.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void claimAllowsAnActiveDelegate() {
        WorkflowState state = state(WorkflowApprovalMode.SINGLE);
        WorkflowInstance instance = instance(state);
        UUID assignee = UUID.randomUUID();
        WorkflowTask t = task(instance, state, assignee);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(java.util.Optional.of(t));
        when(delegations.isActiveDelegateOf(tenantId, assignee, actor)).thenReturn(true);

        service.claim(tenantId, t.getId());

        assertThat(t.getStatus()).isEqualTo(WorkflowTaskStatus.CLAIMED);
    }

    /* ------------------------------- assignAuto() --------------------------------- */

    @Test
    void assignAutoThrowsWhenStateHasNoDefaultApproverRole() {
        WorkflowState state = state(WorkflowApprovalMode.SINGLE);
        WorkflowInstance instance = instance(state);
        when(instanceService.require(tenantId, instance.getId())).thenReturn(instance);

        assertThatThrownBy(
                        () ->
                                service.assignAuto(
                                        tenantId,
                                        instance.getId(),
                                        UUID.randomUUID(),
                                        "APPROVE",
                                        null,
                                        null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignAutoThrowsWhenNoApproverResolves() {
        WorkflowState state = state(WorkflowApprovalMode.SINGLE);
        state.setDefaultApproverRole("MANAGER");
        WorkflowInstance instance = instance(state);
        UUID subjectEmployeeId = UUID.randomUUID();
        when(instanceService.require(tenantId, instance.getId())).thenReturn(instance);
        when(approverResolver.resolve(
                        tenantId, instance.getCompanyId(), subjectEmployeeId, "MANAGER"))
                .thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.assignAuto(
                                        tenantId,
                                        instance.getId(),
                                        subjectEmployeeId,
                                        "APPROVE",
                                        null,
                                        null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void assignAutoCreatesOneTaskPerResolvedApprover() {
        WorkflowState state = state(WorkflowApprovalMode.ALL);
        state.setDefaultApproverRole("FINANCE");
        WorkflowInstance instance = instance(state);
        UUID subjectEmployeeId = UUID.randomUUID();
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        when(instanceService.require(tenantId, instance.getId())).thenReturn(instance);
        when(approverResolver.resolve(
                        tenantId, instance.getCompanyId(), subjectEmployeeId, "FINANCE"))
                .thenReturn(
                        List.of(
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, approver1),
                                new ApproverResolver.ResolvedApprover(
                                        WorkflowActorType.USER, approver2)));
        lenient().when(tasks.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result =
                service.assignAuto(
                        tenantId, instance.getId(), subjectEmployeeId, "APPROVE", null, null);

        assertThat(result).hasSize(2);
        verify(tasks, times(2)).save(any());
    }
}
