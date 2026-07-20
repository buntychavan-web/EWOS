package com.ewos.workflow.domain;

import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral rule enforcer for workflow definition and transition validity.
 *
 * <ul>
 *   <li>Every definition must have exactly one initial state.
 *   <li>Every non-terminal state must have at least one outgoing transition (unless the state is
 *       explicitly terminal).
 *   <li>Transitions must reference states from the same definition.
 *   <li>Only one outgoing transition per (from-state, action-code); enforced in DB by unique index,
 *       re-validated here for early failure.
 *   <li>An instance can only progress if there is a matching outgoing transition from its current
 *       state.
 * </ul>
 */
@Component
public final class WorkflowTransitionPolicy {

    public void assertDefinitionShape(WorkflowDefinition definition) {
        List<WorkflowState> states = definition.getStates();
        if (states == null || states.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Workflow definition has no states");
        }
        long initialCount = states.stream().filter(WorkflowState::isInitial).count();
        if (initialCount != 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Workflow definition must have exactly one initial state (found "
                            + initialCount
                            + ")");
        }
        boolean hasTerminal = states.stream().anyMatch(WorkflowState::isTerminal);
        if (!hasTerminal) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Workflow definition must declare at least one terminal state");
        }
        List<WorkflowTransition> transitions = definition.getTransitions();
        if (transitions != null) {
            for (WorkflowTransition t : transitions) {
                if (t.getFromState() == null || t.getToState() == null) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST, "Transition is missing from/to state");
                }
                if (t.getFromState().isTerminal()) {
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "Terminal state cannot have outgoing transitions: "
                                    + t.getFromState().getCode());
                }
            }
        }
    }

    /**
     * Finds the single transition matching (from-state, action-code) on this definition. Throws 404
     * if no such transition exists; the DB unique index makes >1 impossible.
     */
    public WorkflowTransition resolveTransition(
            WorkflowDefinition definition, WorkflowState fromState, String actionCode) {
        return findTransition(definition, fromState, actionCode)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.CONFLICT,
                                        "No transition '"
                                                + actionCode
                                                + "' from state "
                                                + fromState.getCode()));
    }

    public Optional<WorkflowTransition> findAutoTransition(
            WorkflowDefinition definition, WorkflowState fromState) {
        if (definition.getTransitions() == null) {
            return Optional.empty();
        }
        return definition.getTransitions().stream()
                .filter(WorkflowTransition::isAuto)
                .filter(
                        t ->
                                t.getFromState() != null
                                        && t.getFromState().getId().equals(fromState.getId()))
                .findFirst();
    }

    public void assertInstanceRunning(WorkflowInstance instance) {
        if (instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Workflow instance is not RUNNING (current status: "
                            + instance.getStatus()
                            + ")");
        }
    }

    public void assertTaskOpen(WorkflowTask task) {
        if (task.getStatus() != WorkflowTaskStatus.OPEN
                && task.getStatus() != WorkflowTaskStatus.CLAIMED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Task is not open (current status: " + task.getStatus() + ")");
        }
    }

    private Optional<WorkflowTransition> findTransition(
            WorkflowDefinition definition, WorkflowState fromState, String actionCode) {
        if (definition.getTransitions() == null) {
            return Optional.empty();
        }
        return definition.getTransitions().stream()
                .filter(
                        t ->
                                t.getFromState() != null
                                        && t.getFromState().getId().equals(fromState.getId())
                                        && actionCode != null
                                        && actionCode.equalsIgnoreCase(t.getActionCode()))
                .findFirst();
    }
}
