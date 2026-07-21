package com.ewos.workflow.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowHistoryResponse;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime for workflow instances. Owns the "start / advance / cancel" primitives that downstream
 * modules call to drive their approval flows.
 */
@Service
@Transactional
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository instances;
    private final WorkflowHistoryRepository history;
    private final WorkflowDefinitionService definitions;
    private final WorkflowTransitionPolicy policy;
    private final WorkflowMapper mapper;
    private final ApplicationEventPublisher events;

    public WorkflowInstanceService(
            WorkflowInstanceRepository instances,
            WorkflowHistoryRepository history,
            WorkflowDefinitionService definitions,
            WorkflowTransitionPolicy policy,
            WorkflowMapper mapper,
            ApplicationEventPublisher events) {
        this.instances = instances;
        this.history = history;
        this.definitions = definitions;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    public WorkflowInstanceResponse start(StartInstanceRequest request) {
        WorkflowDefinition def = definitions.require(request.tenantId(), request.definitionId());
        if (!def.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "Workflow definition is inactive");
        }
        if (request.correlationKey() != null && !request.correlationKey().isBlank()) {
            instances
                    .findByTenantIdAndCorrelationKey(request.tenantId(), request.correlationKey())
                    .ifPresent(
                            existing -> {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "Workflow instance already exists for correlation key");
                            });
        }
        WorkflowState initial =
                def.getStates().stream()
                        .filter(WorkflowState::isInitial)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.CONFLICT,
                                                "Workflow definition has no initial state"));

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(request.tenantId());
        instance.setCompanyId(request.companyId());
        instance.setDefinition(def);
        instance.setSubjectType(request.subjectType());
        instance.setSubjectId(request.subjectId());
        instance.setCurrentState(initial);
        instance.setStatus(WorkflowInstanceStatus.RUNNING);
        instance.setStartedAt(Instant.now());
        instance.setCorrelationKey(
                request.correlationKey() != null && !request.correlationKey().isBlank()
                        ? request.correlationKey()
                        : null);

        WorkflowInstance saved = instances.save(instance);
        recordHistory(saved, null, initial, "START", null, null, null);

        publish(WorkflowEventType.INSTANCE_STARTED, saved, null, initial, "START", null);
        publish(WorkflowEventType.STATE_ENTERED, saved, null, initial, null, null);

        maybeAutoAdvance(saved);
        return mapper.toResponse(saved);
    }

    /**
     * Applies an action to advance an instance. Called by {@link WorkflowTaskService} when a task
     * is completed. Also usable directly for system-driven transitions.
     */
    public WorkflowInstance advance(
            WorkflowInstance instance, String actionCode, UUID actorId, UUID taskId, String notes) {
        policy.assertInstanceRunning(instance);
        WorkflowState from = instance.getCurrentState();
        WorkflowTransition transition =
                policy.resolveTransition(instance.getDefinition(), from, actionCode);
        WorkflowState to = transition.getToState();

        instance.setCurrentState(to);
        recordHistory(instance, from, to, actionCode, actorId, notes, taskId);
        publish(WorkflowEventType.STATE_ENTERED, instance, from, to, actionCode, taskId);

        if (to.isTerminal()) {
            instance.setStatus(WorkflowInstanceStatus.COMPLETED);
            instance.setCompletedAt(Instant.now());
            publish(WorkflowEventType.INSTANCE_COMPLETED, instance, from, to, actionCode, taskId);
        } else {
            maybeAutoAdvance(instance);
        }
        return instance;
    }

    public WorkflowInstanceResponse cancel(UUID tenantId, UUID id, String notes) {
        WorkflowInstance instance = require(tenantId, id);
        if (instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only RUNNING instances can be cancelled");
        }
        instance.setStatus(WorkflowInstanceStatus.CANCELLED);
        instance.setCompletedAt(Instant.now());
        recordHistory(
                instance,
                instance.getCurrentState(),
                instance.getCurrentState(),
                "CANCEL",
                currentActor(),
                notes,
                null);
        publish(
                WorkflowEventType.INSTANCE_CANCELLED,
                instance,
                instance.getCurrentState(),
                instance.getCurrentState(),
                "CANCEL",
                null);
        return mapper.toResponse(instance);
    }

    @Transactional(readOnly = true)
    public WorkflowInstanceResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstanceResponse> findBySubject(
            UUID tenantId, String subjectType, UUID subjectId) {
        return instances
                .findAllByTenantIdAndSubjectTypeAndSubjectId(tenantId, subjectType, subjectId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowHistoryResponse> historyOf(UUID tenantId, UUID instanceId) {
        WorkflowInstance instance = require(tenantId, instanceId);
        return history.findAllOfInstance(instance.getId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Package-private lookup used by {@link WorkflowTaskService}. */
    WorkflowInstance require(UUID tenantId, UUID id) {
        return instances
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Workflow instance not found"));
    }

    private void maybeAutoAdvance(WorkflowInstance instance) {
        // Bounded loop; workflow definitions must be acyclic on auto transitions.
        for (int hop = 0; hop < 32; hop++) {
            if (instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
                return;
            }
            Optional<WorkflowTransition> auto =
                    policy.findAutoTransition(instance.getDefinition(), instance.getCurrentState());
            if (auto.isEmpty()) {
                return;
            }
            WorkflowTransition t = auto.get();
            WorkflowState from = instance.getCurrentState();
            WorkflowState to = t.getToState();
            instance.setCurrentState(to);
            recordHistory(instance, from, to, t.getActionCode(), null, null, null);
            publish(WorkflowEventType.STATE_ENTERED, instance, from, to, t.getActionCode(), null);
            if (to.isTerminal()) {
                instance.setStatus(WorkflowInstanceStatus.COMPLETED);
                instance.setCompletedAt(Instant.now());
                publish(
                        WorkflowEventType.INSTANCE_COMPLETED,
                        instance,
                        from,
                        to,
                        t.getActionCode(),
                        null);
                return;
            }
        }
        // Ran off the end of the safety loop — definition has a cycle of auto transitions.
        instance.setStatus(WorkflowInstanceStatus.ERROR);
        instance.setCompletedAt(Instant.now());
        publish(
                WorkflowEventType.INSTANCE_ERRORED,
                instance,
                instance.getCurrentState(),
                instance.getCurrentState(),
                "AUTO_LOOP",
                null);
    }

    private void recordHistory(
            WorkflowInstance instance,
            WorkflowState from,
            WorkflowState to,
            String actionCode,
            UUID actorId,
            String notes,
            UUID taskId) {
        WorkflowHistory row = new WorkflowHistory();
        row.setInstance(instance);
        row.setFromState(from);
        row.setToState(Objects.requireNonNull(to, "toState"));
        row.setActionCode(actionCode);
        row.setActorId(actorId);
        row.setNotes(notes);
        row.setOccurredAt(Instant.now());
        // The workflow_history.task_id FK is captured via the emitted WorkflowEvent's taskId; the
        // history row keeps a lightweight state-transition audit and stays independent of the task
        // row's lifecycle. Left as null on the entity side to avoid an extra load-by-id round trip.
        if (taskId != null) {
            row.setNotes(
                    (row.getNotes() != null ? row.getNotes() + " " : "") + "[task=" + taskId + "]");
        }
        history.save(row);
    }

    private void publish(
            WorkflowEventType type,
            WorkflowInstance instance,
            WorkflowState from,
            WorkflowState to,
            String actionCode,
            UUID taskId) {
        events.publishEvent(
                new WorkflowEvent(
                        type,
                        instance.getId(),
                        instance.getDefinition() != null ? instance.getDefinition().getId() : null,
                        instance.getTenantId(),
                        instance.getCompanyId(),
                        instance.getSubjectType(),
                        instance.getSubjectId(),
                        from != null ? from.getCode() : null,
                        to != null ? to.getCode() : null,
                        actionCode,
                        taskId,
                        currentActor(),
                        Instant.now()));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
