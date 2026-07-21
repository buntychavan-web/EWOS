package com.ewos.workflow.api;

import com.ewos.workflow.api.dto.StateResponse;
import com.ewos.workflow.api.dto.TransitionResponse;
import com.ewos.workflow.api.dto.WorkflowDefinitionResponse;
import com.ewos.workflow.api.dto.WorkflowHistoryResponse;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowHistory;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTransition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Explicit, reflection-free mapping between workflow aggregates and their API record shapes.
 * Follows the same convention as {@code OrganizationMapper} and {@code EmployeeMapper}: greppable
 * field-for-field mapping, no auto-generation.
 */
@Component
public final class WorkflowMapper {

    public StateResponse toResponse(WorkflowState state) {
        return new StateResponse(
                state.getId(),
                state.getCode(),
                state.getName(),
                state.isInitial(),
                state.isTerminal(),
                state.getSortOrder(),
                state.getSlaHours());
    }

    public TransitionResponse toResponse(WorkflowTransition t) {
        WorkflowState from = t.getFromState();
        WorkflowState to = t.getToState();
        return new TransitionResponse(
                t.getId(),
                from != null ? from.getId() : null,
                from != null ? from.getCode() : null,
                to != null ? to.getId() : null,
                to != null ? to.getCode() : null,
                t.getActionCode(),
                t.getRequiredRole(),
                t.isAuto(),
                t.getGuardExpression());
    }

    public WorkflowDefinitionResponse toResponse(WorkflowDefinition definition) {
        List<StateResponse> states = definition.getStates().stream().map(this::toResponse).toList();
        List<TransitionResponse> transitions =
                definition.getTransitions().stream().map(this::toResponse).toList();
        return new WorkflowDefinitionResponse(
                definition.getId(),
                definition.getTenantId(),
                definition.getCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getSubjectType(),
                definition.getDefinitionVersion(),
                definition.isActive(),
                states,
                transitions,
                definition.getCreatedAt(),
                definition.getUpdatedAt(),
                definition.getCreatedBy(),
                definition.getUpdatedBy(),
                definition.getVersionNo());
    }

    public WorkflowInstanceResponse toResponse(WorkflowInstance instance) {
        WorkflowDefinition def = instance.getDefinition();
        WorkflowState current = instance.getCurrentState();
        return new WorkflowInstanceResponse(
                instance.getId(),
                instance.getTenantId(),
                instance.getCompanyId(),
                def != null ? def.getId() : null,
                def != null ? def.getCode() : null,
                def != null ? def.getDefinitionVersion() : 0,
                instance.getSubjectType(),
                instance.getSubjectId(),
                current != null ? current.getId() : null,
                current != null ? current.getCode() : null,
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                instance.getCorrelationKey(),
                instance.getCreatedAt(),
                instance.getUpdatedAt(),
                instance.getCreatedBy(),
                instance.getUpdatedBy(),
                instance.getVersionNo());
    }

    public WorkflowTaskResponse toResponse(WorkflowTask task) {
        WorkflowState state = task.getState();
        return new WorkflowTaskResponse(
                task.getId(),
                task.getTenantId(),
                task.getInstance() != null ? task.getInstance().getId() : null,
                state != null ? state.getId() : null,
                state != null ? state.getCode() : null,
                task.getAssigneeActorType(),
                task.getAssigneeActorId(),
                task.getAssigneeRoleCode(),
                task.getActionCode(),
                task.getDueAt(),
                task.getStatus(),
                task.getCompletedAt(),
                task.getCompletedBy(),
                task.getOutcomeCode(),
                task.getNotes(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getVersionNo());
    }

    public WorkflowHistoryResponse toResponse(WorkflowHistory h) {
        WorkflowState from = h.getFromState();
        WorkflowState to = h.getToState();
        return new WorkflowHistoryResponse(
                h.getId(),
                h.getInstance() != null ? h.getInstance().getId() : null,
                from != null ? from.getId() : null,
                from != null ? from.getCode() : null,
                to != null ? to.getId() : null,
                to != null ? to.getCode() : null,
                h.getActionCode(),
                h.getActorId(),
                h.getTask() != null ? h.getTask().getId() : null,
                h.getNotes(),
                h.getOccurredAt());
    }
}
