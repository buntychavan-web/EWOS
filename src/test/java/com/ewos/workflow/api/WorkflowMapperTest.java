package com.ewos.workflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.workflow.api.dto.WorkflowDefinitionResponse;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.WorkflowTransition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowMapperTest {

    private final WorkflowMapper mapper = new WorkflowMapper();

    @Test
    void definitionMapsStatesAndTransitions() {
        UUID tenant = UUID.randomUUID();
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        def.setTenantId(tenant);
        def.setCode("leave.approval");
        def.setName("Leave Approval");
        def.setSubjectType("leave_request");
        def.setDefinitionVersion(1);
        def.setActive(true);

        WorkflowState draft = state("DRAFT", true, false);
        WorkflowState approved = state("APPROVED", false, true);
        def.getStates().add(draft);
        def.getStates().add(approved);
        WorkflowTransition t = new WorkflowTransition();
        t.setId(UUID.randomUUID());
        t.setFromState(draft);
        t.setToState(approved);
        t.setActionCode("APPROVE");
        def.getTransitions().add(t);

        WorkflowDefinitionResponse r = mapper.toResponse(def);
        assertThat(r.code()).isEqualTo("leave.approval");
        assertThat(r.states()).hasSize(2).extracting("code").containsExactly("DRAFT", "APPROVED");
        assertThat(r.transitions()).hasSize(1);
        assertThat(r.transitions().get(0).fromStateCode()).isEqualTo("DRAFT");
        assertThat(r.transitions().get(0).toStateCode()).isEqualTo("APPROVED");
    }

    @Test
    void instanceMapsCurrentStateAndDefinitionCode() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(UUID.randomUUID());
        def.setCode("leave.approval");
        def.setDefinitionVersion(3);

        WorkflowState current = state("PENDING_MANAGER", false, false);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        instance.setTenantId(UUID.randomUUID());
        instance.setCompanyId(UUID.randomUUID());
        instance.setDefinition(def);
        instance.setSubjectType("leave_request");
        instance.setSubjectId(UUID.randomUUID());
        instance.setCurrentState(current);
        instance.setStatus(WorkflowInstanceStatus.RUNNING);

        WorkflowInstanceResponse r = mapper.toResponse(instance);
        assertThat(r.definitionCode()).isEqualTo("leave.approval");
        assertThat(r.definitionVersion()).isEqualTo(3);
        assertThat(r.currentStateCode()).isEqualTo("PENDING_MANAGER");
        assertThat(r.status()).isEqualTo(WorkflowInstanceStatus.RUNNING);
    }

    @Test
    void taskMapsStateAndAssignee() {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        WorkflowState state = state("PENDING_APPROVAL", false, false);
        WorkflowTask task = new WorkflowTask();
        task.setId(UUID.randomUUID());
        task.setTenantId(UUID.randomUUID());
        task.setInstance(instance);
        task.setState(state);
        task.setAssigneeActorType(WorkflowActorType.EMPLOYEE);
        task.setAssigneeActorId(UUID.randomUUID());
        task.setActionCode("APPROVE");
        task.setStatus(WorkflowTaskStatus.OPEN);

        WorkflowTaskResponse r = mapper.toResponse(task);
        assertThat(r.instanceId()).isEqualTo(instance.getId());
        assertThat(r.stateCode()).isEqualTo("PENDING_APPROVAL");
        assertThat(r.assigneeActorType()).isEqualTo(WorkflowActorType.EMPLOYEE);
    }

    private static WorkflowState state(String code, boolean initial, boolean terminal) {
        WorkflowState s = new WorkflowState();
        s.setId(UUID.randomUUID());
        s.setCode(code);
        s.setName(code);
        s.setInitial(initial);
        s.setTerminal(terminal);
        return s;
    }
}
