package com.ewos.workflow.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.WorkflowMapper;
import com.ewos.workflow.api.dto.AssignTaskRequest;
import com.ewos.workflow.api.dto.CompleteTaskRequest;
import com.ewos.workflow.api.dto.WorkflowTaskResponse;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowApprovalMode;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.WorkflowTransitionPolicy;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ClientAccessGuard guard;
    private final ApproverResolver approverResolver;
    private final WorkflowDelegationService delegations;
    private final ApplicationEventPublisher events;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public WorkflowTaskService(
            WorkflowTaskRepository tasks,
            WorkflowInstanceService instanceService,
            WorkflowTransitionPolicy policy,
            WorkflowMapper mapper,
            ClientAccessGuard guard,
            ApproverResolver approverResolver,
            WorkflowDelegationService delegations,
            ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.instanceService = instanceService;
        this.policy = policy;
        this.mapper = mapper;
        this.guard = guard;
        this.events = events;
        this.approverResolver = approverResolver;
        this.delegations = delegations;
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

        WorkflowTask saved = tasks.save(task);
        publish(WorkflowEventType.TASK_ASSIGNED, instance, saved);
        return mapper.toResponse(saved);
    }

    /**
     * Assigns task(s) at the instance's current state using its {@code defaultApproverRole}
     * instead of a caller-supplied actor (Sprint 4 dynamic approvers). {@code subjectEmployeeId} is
     * the employee the role is resolved relative to (e.g. whose manager to find) — the workflow
     * engine has no notion of "the employee this instance is about" itself, since a subject can be
     * any entity type, so the caller (the owning module) supplies it. A role that resolves to
     * several people (e.g. everyone holding {@code FINANCE}) creates one task per person; combine
     * with {@code approvalMode = ALL} or {@code ANY} on the state to control how many must act.
     */
    public List<WorkflowTaskResponse> assignAuto(
            UUID tenantId,
            UUID instanceId,
            UUID subjectEmployeeId,
            String actionCode,
            Instant dueAt,
            String notes) {
        WorkflowInstance instance = instanceService.require(tenantId, instanceId);
        policy.assertInstanceRunning(instance);
        String approverRole = instance.getCurrentState().getDefaultApproverRole();
        if (approverRole == null || approverRole.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Current state has no default approver role configured — assign explicitly");
        }
        List<ApproverResolver.ResolvedApprover> resolved =
                approverResolver.resolve(
                        tenantId, instance.getCompanyId(), subjectEmployeeId, approverRole);
        if (resolved.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "No approver could be resolved for role '" + approverRole + "'");
        }
        return resolved.stream()
                .map(
                        a ->
                                assign(
                                        tenantId,
                                        instanceId,
                                        new AssignTaskRequest(
                                                a.actorType(), a.actorId(), null, actionCode, dueAt, notes)))
                .toList();
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
                && !task.getAssigneeActorId().equals(actor)
                && !delegations.isActiveDelegateOf(tenantId, task.getAssigneeActorId(), actor)) {
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
        // Records the decision actually taken; the value set at assignment (if any) was only ever
        // a hint — see the class-level note on WorkflowApprovalMode.ALL for why this matters.
        task.setActionCode(request.actionCode());
        if (request.notes() != null) {
            task.setNotes(request.notes());
        }
        publish(WorkflowEventType.TASK_COMPLETED, task.getInstance(), task);

        WorkflowState state = task.getState();
        WorkflowApprovalMode mode = state.getApprovalMode();
        if (mode == WorkflowApprovalMode.SINGLE) {
            instanceService.advance(
                    task.getInstance(), request.actionCode(), actor, task.getId(), request.notes());
            return mapper.toResponse(task);
        }

        List<WorkflowTask> openSiblings = siblingsInState(task, List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED));
        if (mode == WorkflowApprovalMode.ANY) {
            for (WorkflowTask sibling : openSiblings) {
                sibling.setStatus(WorkflowTaskStatus.CANCELLED);
                sibling.setCompletedAt(Instant.now());
                sibling.setNotes(appendNote(sibling.getNotes(), "auto-cancelled: ANY-mode sibling task already completed"));
            }
            instanceService.advance(
                    task.getInstance(), request.actionCode(), actor, task.getId(), request.notes());
            return mapper.toResponse(task);
        }

        // ALL mode: fail-fast on a divergent decision, otherwise wait for every sibling.
        List<WorkflowTask> completedSiblings = siblingsInState(task, List.of(WorkflowTaskStatus.COMPLETED));
        boolean divergent =
                completedSiblings.stream()
                        .anyMatch(
                                t ->
                                        !t.getId().equals(task.getId())
                                                && !request.actionCode().equalsIgnoreCase(t.getActionCode()));
        if (divergent || openSiblings.isEmpty()) {
            // Divergent decision advances immediately (fail-fast); otherwise this was the last
            // outstanding task in the state and every completed sibling agreed — advance now.
            for (WorkflowTask sibling : openSiblings) {
                sibling.setStatus(WorkflowTaskStatus.CANCELLED);
                sibling.setCompletedAt(Instant.now());
                sibling.setNotes(appendNote(sibling.getNotes(), "auto-cancelled: ALL-mode resolved by a divergent decision"));
            }
            instanceService.advance(
                    task.getInstance(), request.actionCode(), actor, task.getId(), request.notes());
        }
        // else: still waiting on other sibling tasks in this state — instance stays put.
        return mapper.toResponse(task);
    }

    private List<WorkflowTask> siblingsInState(WorkflowTask task, List<WorkflowTaskStatus> statuses) {
        return tasks.findAllOfInstanceInStatus(task.getInstance().getId(), statuses).stream()
                .filter(t -> t.getState().getId().equals(task.getState().getId()))
                .toList();
    }

    private static String appendNote(String existing, String addition) {
        return (existing != null && !existing.isBlank() ? existing + " " : "") + "[" + addition + "]";
    }

    @Transactional(readOnly = true)
    public WorkflowTaskResponse getById(UUID tenantId, UUID taskId) {
        return mapper.toResponse(require(tenantId, taskId));
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> myOpenTasks(UUID tenantId, UUID actorId) {
        List<WorkflowTask> found =
                tasks.findAllByTenantIdAndAssigneeActorIdAndStatusIn(
                        tenantId,
                        actorId,
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED));
        guard.requireAccessForCompanies(
                found.stream().map(t -> t.getInstance().getCompanyId()).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowTaskResponse> openTasksForRole(UUID tenantId, String roleCode) {
        List<WorkflowTask> found =
                tasks.findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(
                        tenantId,
                        roleCode,
                        List.of(WorkflowTaskStatus.OPEN, WorkflowTaskStatus.CLAIMED));
        guard.requireAccessForCompanies(
                found.stream().map(t -> t.getInstance().getCompanyId()).toList());
        return found.stream().map(mapper::toResponse).toList();
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
        WorkflowTask task =
                tasks.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Workflow task not found"));
        guard.requireAccessForCompany(task.getInstance().getCompanyId());
        return task;
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

    private void publish(WorkflowEventType type, WorkflowInstance instance, WorkflowTask task) {
        WorkflowState state = task.getState();
        events.publishEvent(
                new WorkflowEvent(
                        type,
                        instance.getId(),
                        instance.getDefinition() != null ? instance.getDefinition().getId() : null,
                        instance.getTenantId(),
                        instance.getCompanyId(),
                        instance.getSubjectType(),
                        instance.getSubjectId(),
                        null,
                        state != null ? state.getCode() : null,
                        task.getActionCode(),
                        task.getId(),
                        currentActor(),
                        Instant.now()));
    }
}
