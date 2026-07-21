package com.ewos.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class WorkflowTransitionPolicyTest {

    private final WorkflowTransitionPolicy policy = new WorkflowTransitionPolicy();

    @Test
    void definitionWithoutStatesRejected() {
        WorkflowDefinition def = new WorkflowDefinition();
        assertThatThrownBy(() -> policy.assertDefinitionShape(def))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no states");
    }

    @Test
    void definitionWithoutInitialStateRejected() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.getStates().add(state("DRAFT", false, false));
        def.getStates().add(state("DONE", false, true));
        assertThatThrownBy(() -> policy.assertDefinitionShape(def))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("initial state");
    }

    @Test
    void definitionWithMultipleInitialsRejected() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.getStates().add(state("DRAFT", true, false));
        def.getStates().add(state("APPROVED", true, false));
        def.getStates().add(state("DONE", false, true));
        assertThatThrownBy(() -> policy.assertDefinitionShape(def))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exactly one initial state");
    }

    @Test
    void definitionWithoutTerminalRejected() {
        WorkflowDefinition def = new WorkflowDefinition();
        def.getStates().add(state("DRAFT", true, false));
        def.getStates().add(state("PENDING", false, false));
        assertThatThrownBy(() -> policy.assertDefinitionShape(def))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminal state");
    }

    @Test
    void terminalWithOutgoingTransitionRejected() {
        WorkflowDefinition def = new WorkflowDefinition();
        WorkflowState draft = state("DRAFT", true, false);
        WorkflowState done = state("DONE", false, true);
        def.getStates().add(draft);
        def.getStates().add(done);
        WorkflowTransition bad = new WorkflowTransition();
        bad.setFromState(done);
        bad.setToState(draft);
        bad.setActionCode("REOPEN");
        def.getTransitions().add(bad);
        assertThatThrownBy(() -> policy.assertDefinitionShape(def))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Terminal state");
    }

    @Test
    void resolveTransitionFindsMatchingEdge() {
        WorkflowDefinition def = new WorkflowDefinition();
        WorkflowState draft = state("DRAFT", true, false);
        WorkflowState submitted = state("SUBMITTED", false, false);
        WorkflowState done = state("DONE", false, true);
        def.getStates().add(draft);
        def.getStates().add(submitted);
        def.getStates().add(done);
        WorkflowTransition submit = trans(draft, submitted, "SUBMIT");
        def.getTransitions().add(submit);
        def.getTransitions().add(trans(submitted, done, "APPROVE"));

        WorkflowTransition resolved = policy.resolveTransition(def, draft, "submit");
        assertThat(resolved).isSameAs(submit);
    }

    @Test
    void resolveTransitionThrowsWhenNoMatch() {
        WorkflowDefinition def = new WorkflowDefinition();
        WorkflowState draft = state("DRAFT", true, false);
        WorkflowState done = state("DONE", false, true);
        def.getStates().add(draft);
        def.getStates().add(done);
        def.getTransitions().add(trans(draft, done, "APPROVE"));

        assertThatThrownBy(() -> policy.resolveTransition(def, draft, "REJECT"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void findAutoTransitionReturnsAutoOnlyEdge() {
        WorkflowDefinition def = new WorkflowDefinition();
        WorkflowState draft = state("DRAFT", true, false);
        WorkflowState submitted = state("SUBMITTED", false, false);
        WorkflowState done = state("DONE", false, true);
        def.getStates().add(draft);
        def.getStates().add(submitted);
        def.getStates().add(done);
        WorkflowTransition manual = trans(draft, submitted, "SUBMIT");
        WorkflowTransition auto = trans(submitted, done, "AUTO_COMPLETE");
        auto.setAuto(true);
        def.getTransitions().add(manual);
        def.getTransitions().add(auto);

        assertThat(policy.findAutoTransition(def, draft)).isEmpty();
        assertThat(policy.findAutoTransition(def, submitted)).contains(auto);
    }

    @Test
    void instanceRunningGuard() {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setStatus(WorkflowInstanceStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertInstanceRunning(instance))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not RUNNING");
    }

    @Test
    void taskOpenGuardAllowsClaimed() {
        WorkflowTask task = new WorkflowTask();
        task.setStatus(WorkflowTaskStatus.CLAIMED);
        policy.assertTaskOpen(task); // no throw

        task.setStatus(WorkflowTaskStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertTaskOpen(task)).isInstanceOf(ApiException.class);
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

    private static WorkflowTransition trans(
            WorkflowState from, WorkflowState to, String actionCode) {
        WorkflowTransition t = new WorkflowTransition();
        t.setId(UUID.randomUUID());
        t.setFromState(from);
        t.setToState(to);
        t.setActionCode(actionCode);
        return t;
    }
}
