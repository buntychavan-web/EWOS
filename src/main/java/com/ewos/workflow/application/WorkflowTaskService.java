package com.ewos.workflow.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.CompleteTaskRequest;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WorkflowTaskService {

    private final WorkflowTaskRepository tasks;
    private final WorkflowInstanceService instanceService;
    private final WorkflowTransitionPolicy policy;
    private final WorkflowMapper mapper;

    public WorkflowTaskService(
            WorkflowTaskRepository tasks,
            WorkflowInstanceService instanceService,
            WorkflowTransitionPolicy policy,
            WorkflowMapper mapper) {
        this.tasks = tasks;
        this.instanceService = instanceService;
        this.policy = policy;
        this.mapper = mapper;
    }

    /** Assigns a new human task against the current state of an instance. */
    public WorkflowTaskResponse assign(UUID tenantId, UUID instanceId, AssignTaskRequest request) {
        WorkflowInstance instance = instanceService.require(tenantId, instanceId);
        policy.assertInstanceRunning(instance);

        if (request.assigneeActorType() == WorkflowActorType.ROLE) {
            if (request.assigneeRoleCode() == null || request.assigneeRoleCode().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "assigneeRoleCode required when actor type is ROLE");
            }
        } else if (request.assigneeActorId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "assigneeActorId required when actor type is " + request.assigneeActorType());
        }

        WorkflowTask task = new WorkflowTask();
        task.setTenantId(tenantId);
        task.setInstance(instance);
        task.setState(instance.getCurrentState());
        task.setAssigneeActorType(request.assigneeActorType());
        task.setAssigneeActorId(request.assigneeActorId());
        task.setAssigneeRoleCode(request.assigneeRoleCode());
        task.setActionCode(request.actionCode());
        task.setDueAt(request.dueAt());
        task.setStatus(WorkflowTaskStatus.OPEN);
        task.setNotes(request.notes());

        return mapper.toResponse(tasks.save(task));
    }

    public WorkflowTaskResponse claim(UUID tenantId, UUID taskId) {
        WorkflowTask task = require(tenantId, taskId);
        policy.assertTaskOpen(task);
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required to claim a task");
        }
        if (task.getAssigneeActorType() != WorkflowActorType.ROLE
                && task.getAssigneeActorId() != null
                && !task.getAssigneeActorId().equals(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Task is assigned to a different actor");
        }
        task.setStatus(WorkflowTaskStatus.CLAIMED);
        if (task.getAssigneeActorType() == WorkflowActorType.ROLE) {
            task.setAssigneeActorType(WorkflowActorType.USER);
            task.setAssigneeActorId(actor);
        }
        return mapper.toResponse(task);
    }

    public WorkflowTaskResponse complete(UUID tenantId, UUID taskId, CompleteTaskRequest request) {
        WorkflowTask task = require(tenantId, taskId);
        policy.assertTaskOpen(task);
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required to complete a task");
        }
        task.setStatus(WorkflowTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setCompletedBy(actor);
        task.setOutcomeCode(request.outcomeCode());
        if (request.notes() != null) {
            task.setNotes(request.notes());
        }

        instanceService.advance(
                task.getInstance(), request.actionCode(), actor, task.getId(), request.notes());
        return mapper.toResponse(task);
    }

    @Transactional(readOnly = true)
    public WorkflowTaskResponse getById(UUID tenantId, UUID taskId) {
        return mapper.toResponse(require(tenantId, taskId));
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> myOpenTasks(UUID tenantId, UUID actorId) {
        return tasks
                .findAllByTenantIdAndAssigneeActorIdAndStatusIn(
                        tenantId,
                        actorId,
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> openTasksForRole(UUID tenantId, String roleCode) {
        return tasks
                .findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(
                        tenantId,
                        roleCode,
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED))
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> tasksOfInstance(UUID tenantId, UUID instanceId) {
        // Tenant scoping enforced by verifying the instance belongs to the tenant.
        WorkflowInstance instance = instanceService.require(tenantId, instanceId);
        return tasks.findAllOfInstance(instance.getId()).stream().map(mapper::toResponse).toList();
    }

    public void cancel(UUID tenantId, UUID taskId) {
        WorkflowTask task = require(tenantId, taskId);
        policy.assertTaskOpen(task);
        task.setStatus(WorkflowTaskStatus.CANCELLED);
        task.setCompletedAt(Instant.now());
    }

    private WorkflowTask require(UUID tenantId, UUID id) {
        return tasks.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Workflow task not found"));
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
