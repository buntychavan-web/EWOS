package com.ewos.competency.application;

import com.ewos.competency.api.CompetencyMapper;
import com.ewos.competency.api.dto.ActionResponse;
import com.ewos.competency.api.dto.AddActionRequest;
import com.ewos.competency.api.dto.CompetencyDashboardResponse;
import com.ewos.competency.api.dto.CreatePlanRequest;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.DevelopmentAction;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.DevelopmentPlanStatus;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.DevelopmentActionRepository;
import com.ewos.competency.infrastructure.persistence.DevelopmentPlanRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DevelopmentPlanService {

    private final DevelopmentPlanRepository plans;
    private final DevelopmentActionRepository actions;
    private final CompetencyService competencies;
    private final EmployeeRepository employees;
    private final CompetencyMapper mapper;
    private final ApplicationEventPublisher events;

    public DevelopmentPlanService(
            DevelopmentPlanRepository plans,
            DevelopmentActionRepository actions,
            CompetencyService competencies,
            EmployeeRepository employees,
            CompetencyMapper mapper,
            ApplicationEventPublisher events) {
        this.plans = plans;
        this.actions = actions;
        this.competencies = competencies;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    public PlanResponse create(CreatePlanRequest req) {
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (req.startsOn() != null
                && req.endsOn() != null
                && !req.endsOn().isAfter(req.startsOn())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ends_on must be strictly after starts_on");
        }
        DevelopmentPlan p = new DevelopmentPlan();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setEmployee(employee);
        p.setTitle(req.title());
        p.setDescription(req.description());
        p.setStartsOn(req.startsOn());
        p.setEndsOn(req.endsOn());
        p.setStatus(DevelopmentPlanStatus.DRAFT);
        p = plans.save(p);
        publish(CompetencyEventType.PLAN_CREATED, p);
        return mapper.toResponse(p);
    }

    public PlanResponse activate(UUID tenantId, UUID id) {
        DevelopmentPlan p = require(tenantId, id);
        if (p.getStatus() != DevelopmentPlanStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT plans can be activated (status=" + p.getStatus() + ")");
        }
        p.setStatus(DevelopmentPlanStatus.ACTIVE);
        publish(CompetencyEventType.PLAN_ACTIVATED, p);
        return mapper.toResponse(p);
    }

    public PlanResponse complete(UUID tenantId, UUID id) {
        DevelopmentPlan p = require(tenantId, id);
        if (p.getStatus() != DevelopmentPlanStatus.ACTIVE
                && p.getStatus() != DevelopmentPlanStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Plan cannot be completed from status " + p.getStatus());
        }
        p.setStatus(DevelopmentPlanStatus.COMPLETED);
        p.setCompletedAt(Instant.now());
        publish(CompetencyEventType.PLAN_COMPLETED, p);
        return mapper.toResponse(p);
    }

    public PlanResponse cancel(UUID tenantId, UUID id) {
        DevelopmentPlan p = require(tenantId, id);
        if (p.getStatus() == DevelopmentPlanStatus.COMPLETED
                || p.getStatus() == DevelopmentPlanStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Plan is already terminal (status=" + p.getStatus() + ")");
        }
        p.setStatus(DevelopmentPlanStatus.CANCELLED);
        publish(CompetencyEventType.PLAN_CANCELLED, p);
        return mapper.toResponse(p);
    }

    public ActionResponse addAction(UUID tenantId, UUID planId, AddActionRequest req) {
        DevelopmentPlan p = require(tenantId, planId);
        if (p.getStatus() == DevelopmentPlanStatus.COMPLETED
                || p.getStatus() == DevelopmentPlanStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot add actions to a terminal plan (status=" + p.getStatus() + ")");
        }
        Competency c =
                req.competencyId() == null
                        ? null
                        : competencies.require(tenantId, req.competencyId());
        DevelopmentAction a = new DevelopmentAction();
        a.setTenantId(tenantId);
        a.setPlan(p);
        a.setCompetency(c);
        a.setAction(req.action());
        a.setDueOn(req.dueOn());
        a.setNotes(req.notes());
        a = actions.save(a);
        publish(CompetencyEventType.ACTION_ADDED, p);
        return mapper.toResponse(a);
    }

    public ActionResponse completeAction(UUID tenantId, UUID actionId) {
        DevelopmentAction a =
                actions.findByIdAndTenantId(actionId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Development action not found"));
        if (a.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "Action already completed");
        }
        a.setCompleted(true);
        a.setCompletedAt(Instant.now());
        if (a.getPlan() != null) {
            publish(CompetencyEventType.ACTION_COMPLETED, a.getPlan());
        }
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public PlanResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return plans.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> byStatus(
            UUID tenantId, UUID companyId, DevelopmentPlanStatus status) {
        return plans.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActionResponse> actionsFor(UUID tenantId, UUID planId) {
        return actions.findAllByTenantIdAndPlanId(tenantId, planId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompetencyDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        return new CompetencyDashboardResponse(
                competencies.activeCount(tenantId, companyId),
                plans.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, DevelopmentPlanStatus.DRAFT),
                plans.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, DevelopmentPlanStatus.ACTIVE),
                plans.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, DevelopmentPlanStatus.COMPLETED),
                plans.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, DevelopmentPlanStatus.CANCELLED));
    }

    private DevelopmentPlan require(UUID tenantId, UUID id) {
        return plans.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Development plan not found"));
    }

    private void publish(CompetencyEventType type, DevelopmentPlan p) {
        events.publishEvent(
                new CompetencyEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        null,
                        p.getEmployee() == null ? null : p.getEmployee().getId(),
                        p.getId(),
                        null,
                        null,
                        CompetencySecurity.currentActor(),
                        Instant.now()));
    }
}
