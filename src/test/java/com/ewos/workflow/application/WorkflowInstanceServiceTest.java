package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowHistory;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTransition;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowHistoryRepository;
import com.ewos.workflow.infrastructure.persistence.WorkflowInstanceRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WorkflowInstanceServiceTest {

    @Mock WorkflowInstanceRepository instances;
    @Mock WorkflowHistoryRepository history;
    @Mock WorkflowDefinitionService definitions;
    @Mock ApplicationEventPublisher events;

    private final WorkflowTransitionPolicy policy = new WorkflowTransitionPolicy();
    private WorkflowInstanceService service;

    @BeforeEach
    void setUp() {
        service =
                new WorkflowInstanceService(
                        instances, history, definitions, policy, new WorkflowMapper(), events);
        lenient()
                .when(instances.save(any(WorkflowInstance.class)))
                .thenAnswer(
                        inv -> {
                            WorkflowInstance i = inv.getArgument(0);
                            if (i.getId() == null) {
                                i.setId(UUID.randomUUID());
                            }
                            return i;
                        });
        lenient()
                .when(history.save(any(WorkflowHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void startInitialStateAndEmitsEvents() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        when(definitions.require(tenant, def.getId())).thenReturn(def);

        service.start(
                new StartInstanceRequest(
                        tenant, company, def.getId(), "leave_request", UUID.randomUUID(), null));

        ArgumentCaptor<WorkflowEvent> ev = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(events, org.mockito.Mockito.atLeast(2)).publishEvent(ev.capture());
        assertThat(ev.getAllValues())
                .extracting(WorkflowEvent::eventType)
                .contains(WorkflowEventType.INSTANCE_STARTED, WorkflowEventType.STATE_ENTERED);
    }

    @Test
    void startRejectsInactiveDefinition() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        def.setActive(false);
        when(definitions.require(tenant, def.getId())).thenReturn(def);

        assertThatThrownBy(
                        () ->
                                service.start(
                                        new StartInstanceRequest(
                                                tenant,
                                                company,
                                                def.getId(),
                                                "leave_request",
                                                UUID.randomUUID(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void startRejectsDuplicateCorrelationKey() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        when(definitions.require(tenant, def.getId())).thenReturn(def);
        when(instances.findByTenantIdAndCorrelationKey(tenant, "leave-42"))
                .thenReturn(Optional.of(new WorkflowInstance()));

        assertThatThrownBy(
                        () ->
                                service.start(
                                        new StartInstanceRequest(
                                                tenant,
                                                company,
                                                def.getId(),
                                                "leave_request",
                                                UUID.randomUUID(),
                                                "leave-42")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("correlation");
    }

    @Test
    void advanceCompletesInstanceOnTerminalState() {
        UUID tenant = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        instance.setTenantId(tenant);
        instance.setCompanyId(UUID.randomUUID());
        instance.setDefinition(def);
        instance.setSubjectType("leave_request");
        instance.setSubjectId(UUID.randomUUID());
        instance.setCurrentState(def.getStates().get(0));
        instance.setStatus(WorkflowInstanceStatus.RUNNING);

        service.advance(instance, "APPROVE", UUID.randomUUID(), null, "approved");

        assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
        assertThat(instance.getCompletedAt()).isNotNull();
        assertThat(instance.getCurrentState().getCode()).isEqualTo("APPROVED");
    }

    @Test
    void advanceRejectsUnknownAction() {
        UUID tenant = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        WorkflowInstance instance = runningInstance(def);
        assertThatThrownBy(() -> service.advance(instance, "REJECT", null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No transition");
    }

    @Test
    void cancelSetsCancelledStatusAndEmitsEvent() {
        UUID tenant = UUID.randomUUID();
        WorkflowDefinition def = twoStateDef(tenant);
        WorkflowInstance instance = runningInstance(def);
        instance.setTenantId(tenant);
        when(instances.findByIdAndTenantId(instance.getId(), tenant))
                .thenReturn(Optional.of(instance));

        service.cancel(tenant, instance.getId(), "no longer needed");

        assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
        assertThat(instance.getCompletedAt()).isNotNull();
    }

    private WorkflowDefinition twoStateDef(UUID tenant) {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        def.setTenantId(tenant);
        def.setCode("leave.approval");
        def.setDefinitionVersion(1);
        def.setActive(true);

        WorkflowState draft = new WorkflowState();
        draft.setId(UUID.randomUUID());
        draft.setCode("DRAFT");
        draft.setName("Draft");
        draft.setInitial(true);
        draft.setDefinition(def);

        WorkflowState approved = new WorkflowState();
        approved.setId(UUID.randomUUID());
        approved.setCode("APPROVED");
        approved.setName("Approved");
        approved.setTerminal(true);
        approved.setDefinition(def);

        def.getStates().add(draft);
        def.getStates().add(approved);

        WorkflowTransition t = new WorkflowTransition();
        t.setId(UUID.randomUUID());
        t.setDefinition(def);
        t.setFromState(draft);
        t.setToState(approved);
        t.setActionCode("APPROVE");
        def.getTransitions().add(t);
        return def;
    }

    private WorkflowInstance runningInstance(WorkflowDefinition def) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        instance.setCompanyId(UUID.randomUUID());
        instance.setDefinition(def);
        instance.setSubjectType("leave_request");
        instance.setSubjectId(UUID.randomUUID());
        instance.setCurrentState(def.getStates().get(0));
        instance.setStatus(WorkflowInstanceStatus.RUNNING);
        return instance;
    }
}
