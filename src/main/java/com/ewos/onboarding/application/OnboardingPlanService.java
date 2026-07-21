package com.ewos.onboarding.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.onboarding.api.OnboardingMapper;
import com.ewos.onboarding.api.dto.AddOnboardingTaskRequest;
import com.ewos.onboarding.api.dto.AssignPlanRolesRequest;
import com.ewos.onboarding.api.dto.CreateOnboardingPlanRequest;
import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.onboarding.api.dto.UpdateOnboardingTaskStatusRequest;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import com.ewos.onboarding.domain.OnboardingPolicy;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskStatus;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import com.ewos.onboarding.domain.events.OnboardingEvent;
import com.ewos.onboarding.domain.events.OnboardingEventType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingPlanRepository;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskInstanceRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns onboarding plans and the tasks under them. Plans are typically created automatically by the
 * {@link com.ewos.onboarding.application.PreboardingJoinedListener handoff listener} when a T4
 * checklist reaches JOINED, but the {@code POST /plans} endpoint lets HR create one manually too.
 */
@Service
@Transactional
public class OnboardingPlanService {

    private final OnboardingPlanRepository plans;
    private final OnboardingTaskInstanceRepository tasks;
    private final OnboardingTaskTemplateService templates;
    private final EmployeeRepository employees;
    private final OnboardingPolicy policy;
    private final OnboardingMapper mapper;
    private final ApplicationEventPublisher events;

    public OnboardingPlanService(
            OnboardingPlanRepository plans,
            OnboardingTaskInstanceRepository tasks,
            OnboardingTaskTemplateService templates,
            EmployeeRepository employees,
            OnboardingPolicy policy,
            OnboardingMapper mapper,
            ApplicationEventPublisher events) {
        this.plans = plans;
        this.tasks = tasks;
        this.templates = templates;
        this.employees = employees;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    public OnboardingPlanResponse create(CreateOnboardingPlanRequest req) {
        return mapper.toResponse(createInternal(req));
    }

    /**
     * Package-visible: the handoff listener bypasses the DTO validation and constructs a plan
     * directly from an offer + checklist. Returns the plan even if it already existed (idempotent
     * by employee).
     */
    OnboardingPlan createInternal(CreateOnboardingPlanRequest req) {
        OnboardingPlan existing =
                plans.findByTenantIdAndEmployeeId(req.tenantId(), req.employeeId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(req.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee does not belong to the given company");
        }

        OnboardingPlan p = new OnboardingPlan();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setEmployee(employee);
        p.setSourceOfferId(req.sourceOfferId());
        p.setSourceChecklistId(req.sourceChecklistId());
        p.setJoiningDate(req.joiningDate());
        p.setManagerEmployee(resolveEmployee(req.tenantId(), req.managerEmployeeId()));
        p.setBuddyEmployee(resolveEmployee(req.tenantId(), req.buddyEmployeeId()));
        p.setNotes(req.notes());
        p.setStatus(OnboardingPlanStatus.PLANNED);
        p.setCompletionPercent(BigDecimal.ZERO);
        p = plans.save(p);

        publish(OnboardingEventType.PLAN_CREATED, p, null, null, null);
        materialiseTemplateTasks(p);
        recomputeCompletion(p);
        return p;
    }

    public OnboardingPlanResponse start(UUID tenantId, UUID id) {
        OnboardingPlan p = require(tenantId, id);
        policy.assertPlanStartable(p);
        p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
        p.setStartedAt(Instant.now());
        publish(OnboardingEventType.PLAN_STARTED, p, null, null, null);
        return mapper.toResponse(p);
    }

    public OnboardingPlanResponse complete(UUID tenantId, UUID id) {
        OnboardingPlan p = require(tenantId, id);
        policy.assertPlanCompletable(p);
        if (hasOutstandingMandatoryTasks(p)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot complete a plan while mandatory tasks are outstanding");
        }
        p.setStatus(OnboardingPlanStatus.COMPLETED);
        p.setCompletedAt(Instant.now());
        p.setCompletedBy(OnboardingSecurity.currentActor());
        publish(OnboardingEventType.PLAN_COMPLETED, p, null, null, null);
        return mapper.toResponse(p);
    }

    public OnboardingPlanResponse cancel(UUID tenantId, UUID id, String reason) {
        OnboardingPlan p = require(tenantId, id);
        policy.assertPlanCancellable(p);
        p.setStatus(OnboardingPlanStatus.CANCELLED);
        p.setNotes(reason);
        publish(OnboardingEventType.PLAN_CANCELLED, p, null, null, reason);
        return mapper.toResponse(p);
    }

    public OnboardingPlanResponse assignRoles(UUID tenantId, UUID id, AssignPlanRolesRequest req) {
        OnboardingPlan p = require(tenantId, id);
        policy.assertPlanMutable(p);
        Employee prevBuddy = p.getBuddyEmployee();
        Employee prevManager = p.getManagerEmployee();
        p.setManagerEmployee(resolveEmployee(tenantId, req.managerEmployeeId()));
        p.setBuddyEmployee(resolveEmployee(tenantId, req.buddyEmployeeId()));
        publish(OnboardingEventType.PLAN_UPDATED, p, null, null, null);
        if (!java.util.Objects.equals(idOf(prevBuddy), idOf(p.getBuddyEmployee()))) {
            publish(OnboardingEventType.BUDDY_ASSIGNED, p, null, null, null);
        }
        if (!java.util.Objects.equals(idOf(prevManager), idOf(p.getManagerEmployee()))) {
            publish(OnboardingEventType.MANAGER_ASSIGNED, p, null, null, null);
        }
        return mapper.toResponse(p);
    }

    public OnboardingTaskInstanceResponse addTask(
            UUID tenantId, UUID planId, AddOnboardingTaskRequest req) {
        OnboardingPlan p = require(tenantId, planId);
        policy.assertPlanMutable(p);

        OnboardingTaskTemplate tpl = null;
        if (req.templateId() != null) {
            tpl =
                    templates.activeTemplatesFor(tenantId, p.getCompanyId()).stream()
                            .filter(t -> t.getId().equals(req.templateId()))
                            .findFirst()
                            .orElse(null);
            if (tpl == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Onboarding task template not active for company");
            }
        }

        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setTenantId(tenantId);
        t.setPlan(p);
        t.setTemplate(tpl);
        t.setName(req.name());
        t.setTaskType(req.taskType());
        t.setOwner(req.owner() == null ? OnboardingTaskOwner.HR : req.owner());
        t.setAssignedEmployee(resolveEmployee(tenantId, req.assignedEmployeeId()));
        t.setMandatory(req.mandatory() == null ? true : req.mandatory());
        if (req.sortOrder() != null) {
            t.setSortOrder(req.sortOrder());
        } else if (tpl != null) {
            t.setSortOrder(tpl.getSortOrder());
        }
        t.setDueDate(req.dueDate());
        t.setNotes(req.notes());
        t = tasks.save(t);
        publish(OnboardingEventType.TASK_CREATED, p, t.getId(), null, req.taskType().name());
        if (t.getAssignedEmployee() != null) {
            publish(OnboardingEventType.TASK_ASSIGNED, p, t.getId(), null, null);
        }
        recomputeCompletion(p);
        return mapper.toResponse(t);
    }

    public OnboardingTaskInstanceResponse updateTaskStatus(
            UUID tenantId, UUID taskId, UpdateOnboardingTaskStatusRequest req) {
        OnboardingTaskInstance t =
                tasks.findByIdAndTenantId(taskId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Onboarding task not found"));
        policy.assertTaskEditable(t);
        applyTaskStatus(t, req);
        OnboardingPlan p = t.getPlan();
        if (p.getStatus() == OnboardingPlanStatus.PLANNED) {
            p.setStatus(OnboardingPlanStatus.IN_PROGRESS);
            p.setStartedAt(Instant.now());
        }
        OnboardingEventType type =
                switch (req.status()) {
                    case COMPLETED -> OnboardingEventType.TASK_COMPLETED;
                    case SKIPPED -> OnboardingEventType.TASK_SKIPPED;
                    case FAILED -> OnboardingEventType.TASK_FAILED;
                    default -> OnboardingEventType.TASK_STARTED;
                };
        publish(type, p, t.getId(), null, req.status().name());
        recomputeCompletion(p);
        return mapper.toResponse(t);
    }

    public OnboardingTaskInstanceResponse remindTask(UUID tenantId, UUID taskId) {
        OnboardingTaskInstance t =
                tasks.findByIdAndTenantId(taskId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Onboarding task not found"));
        if (t.isTerminal()) {
            throw new ApiException(HttpStatus.CONFLICT, "Task is already terminal");
        }
        publish(OnboardingEventType.TASK_REMINDER_SENT, t.getPlan(), t.getId(), null, t.getName());
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public OnboardingPlanResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public OnboardingPlanResponse forEmployee(UUID tenantId, UUID employeeId) {
        return plans.findByTenantIdAndEmployeeId(tenantId, employeeId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No onboarding plan for this employee"));
    }

    @Transactional(readOnly = true)
    public List<OnboardingPlanResponse> byStatus(
            UUID tenantId, UUID companyId, OnboardingPlanStatus status) {
        return plans.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OnboardingTaskInstanceResponse> tasksFor(UUID tenantId, UUID planId) {
        require(tenantId, planId);
        return tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, planId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    OnboardingPlan require(UUID tenantId, UUID id) {
        return plans.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Onboarding plan not found"));
    }

    private void materialiseTemplateTasks(OnboardingPlan p) {
        List<OnboardingTaskTemplate> active =
                templates.activeTemplatesFor(p.getTenantId(), p.getCompanyId());
        for (OnboardingTaskTemplate tpl : active) {
            OnboardingTaskInstance t = new OnboardingTaskInstance();
            t.setTenantId(p.getTenantId());
            t.setPlan(p);
            t.setTemplate(tpl);
            t.setName(tpl.getName());
            t.setTaskType(tpl.getTaskType());
            t.setOwner(tpl.getDefaultOwner());
            t.setMandatory(tpl.isMandatory());
            t.setSortOrder(tpl.getSortOrder());
            if (tpl.getDefaultSlaDays() != null && p.getJoiningDate() != null) {
                t.setDueDate(p.getJoiningDate().plusDays(tpl.getDefaultSlaDays()));
            }
            tasks.save(t);
            publish(OnboardingEventType.TASK_CREATED, p, t.getId(), null, tpl.getTaskType().name());
        }
    }

    private void applyTaskStatus(OnboardingTaskInstance t, UpdateOnboardingTaskStatusRequest req) {
        Instant now = Instant.now();
        if (req.status() == OnboardingTaskStatus.IN_PROGRESS && t.getStartedAt() == null) {
            t.setStartedAt(now);
        }
        if (req.status() == OnboardingTaskStatus.COMPLETED
                || req.status() == OnboardingTaskStatus.SKIPPED
                || req.status() == OnboardingTaskStatus.FAILED) {
            t.setCompletedAt(now);
            t.setCompletedBy(OnboardingSecurity.currentActor());
        }
        t.setStatus(req.status());
        if (req.notes() != null) {
            t.setNotes(req.notes());
        }
        if (req.resultJson() != null) {
            t.setResultJson(req.resultJson());
        }
    }

    private boolean hasOutstandingMandatoryTasks(OnboardingPlan p) {
        return tasks
                .findAllByTenantIdAndPlanIdOrderBySortOrderAsc(p.getTenantId(), p.getId())
                .stream()
                .anyMatch(t -> t.isMandatory() && !t.isTerminal());
    }

    private void recomputeCompletion(OnboardingPlan p) {
        List<OnboardingTaskInstance> siblings =
                tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(p.getTenantId(), p.getId());
        if (siblings.isEmpty()) {
            p.setCompletionPercent(BigDecimal.ZERO);
            return;
        }
        long done = siblings.stream().filter(OnboardingTaskInstance::isTerminal).count();
        BigDecimal pct =
                new BigDecimal(done * 100L)
                        .divide(new BigDecimal(siblings.size()), 2, RoundingMode.HALF_UP);
        p.setCompletionPercent(pct);
    }

    private Employee resolveEmployee(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private static UUID idOf(Employee e) {
        return e == null ? null : e.getId();
    }

    private void publish(
            OnboardingEventType type, OnboardingPlan p, UUID taskId, UUID surveyId, String detail) {
        events.publishEvent(
                new OnboardingEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        p.getId(),
                        p.getEmployee() == null ? null : p.getEmployee().getId(),
                        taskId,
                        surveyId,
                        detail,
                        OnboardingSecurity.currentActor(),
                        Instant.now()));
    }
}
