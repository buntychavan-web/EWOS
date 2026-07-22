package com.ewos.goals.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.goals.api.GoalMapper;
import com.ewos.goals.api.dto.CreateGoalRequest;
import com.ewos.goals.api.dto.GoalDashboardResponse;
import com.ewos.goals.api.dto.GoalProgressResponse;
import com.ewos.goals.api.dto.GoalReportRowResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.api.dto.GoalReviewRequest;
import com.ewos.goals.api.dto.ProgressUpdateRequest;
import com.ewos.goals.api.dto.UpdateGoalRequest;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalLibraryItem;
import com.ewos.goals.domain.GoalLifecyclePolicy;
import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalProgressUpdate;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.domain.events.GoalEventType;
import com.ewos.goals.infrastructure.persistence.GoalProgressUpdateRepository;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoalService {

    private final GoalRepository goals;
    private final GoalProgressUpdateRepository progress;
    private final EmployeeRepository employees;
    private final GoalLibraryService library;
    private final GoalLifecyclePolicy lifecycle;
    private final GoalMapper mapper;
    private final ApplicationEventPublisher events;

    public GoalService(
            GoalRepository goals,
            GoalProgressUpdateRepository progress,
            EmployeeRepository employees,
            GoalLibraryService library,
            GoalLifecyclePolicy lifecycle,
            GoalMapper mapper,
            ApplicationEventPublisher events) {
        this.goals = goals;
        this.progress = progress;
        this.employees = employees;
        this.library = library;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
    }

    public GoalResponse create(CreateGoalRequest req) {
        if (goals.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Goal code already exists: " + req.code());
        }
        GoalLibraryItem lib =
                req.libraryGoalId() == null
                        ? null
                        : library.require(req.tenantId(), req.libraryGoalId());
        Goal parent =
                req.parentGoalId() == null ? null : requireGoal(req.tenantId(), req.parentGoalId());
        Employee employee =
                req.employeeId() == null
                        ? null
                        : employees
                                .findByIdAndTenantId(req.employeeId(), req.tenantId())
                                .orElseThrow(
                                        () ->
                                                new ApiException(
                                                        HttpStatus.BAD_REQUEST,
                                                        "Employee not found"));
        Goal g = new Goal();
        g.setTenantId(req.tenantId());
        g.setCompanyId(req.companyId());
        g.setLibraryGoal(lib);
        g.setParentGoal(parent);
        g.setCode(req.code());
        g.setName(req.name());
        g.setDescription(req.description());
        g.setGoalType(req.goalType());
        g.setScope(req.scope());
        g.setEmployee(employee);
        g.setOrgUnitId(req.orgUnitId());
        g.setPerformanceCycleId(req.performanceCycleId());
        g.setPeriodStart(req.periodStart());
        g.setPeriodEnd(req.periodEnd());
        g.setWeightage(req.weightage() == null ? BigDecimal.ZERO : req.weightage());
        g.setTarget(req.target());
        g.setUnitOfMeasure(req.unitOfMeasure());
        g.setPriority(req.priority() == null ? GoalPriority.MEDIUM : req.priority());
        g.setStatus(GoalStatus.DRAFT);
        lifecycle.assertOpenable(g);
        g = goals.save(g);
        publish(GoalEventType.GOAL_CREATED, g, null);
        return mapper.toResponse(g);
    }

    public GoalResponse update(UUID tenantId, UUID id, UpdateGoalRequest req) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertUpdatable(g);
        g.setName(req.name());
        g.setDescription(req.description());
        g.setWeightage(req.weightage() == null ? BigDecimal.ZERO : req.weightage());
        g.setTarget(req.target());
        g.setUnitOfMeasure(req.unitOfMeasure());
        if (req.priority() != null) {
            g.setPriority(req.priority());
        }
        publish(GoalEventType.GOAL_UPDATED, g, null);
        return mapper.toResponse(g);
    }

    public GoalResponse assign(UUID tenantId, UUID id) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertAssignable(g);
        g.setStatus(GoalStatus.ASSIGNED);
        publish(GoalEventType.GOAL_ASSIGNED, g, null);
        return mapper.toResponse(g);
    }

    public GoalProgressResponse recordProgress(UUID tenantId, UUID id, ProgressUpdateRequest req) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertProgressRecordable(g);
        lifecycle.assertProgressValueValid(req.progressPercent());
        g.setCurrentValue(req.currentValue());
        g.setProgressPercent(req.progressPercent());
        if (g.getStatus() == GoalStatus.ASSIGNED) {
            g.setStatus(GoalStatus.IN_PROGRESS);
        }
        GoalProgressUpdate u = new GoalProgressUpdate();
        u.setTenantId(tenantId);
        u.setGoal(g);
        u.setCurrentValue(req.currentValue());
        u.setProgressPercent(req.progressPercent());
        u.setNotes(req.notes());
        u.setRecordedAt(Instant.now());
        u.setRecordedBy(GoalSecurity.currentActor());
        u = progress.save(u);
        publish(GoalEventType.GOAL_PROGRESS_RECORDED, g, req.progressPercent().toPlainString());
        return mapper.toResponse(u);
    }

    public GoalResponse submitForReview(UUID tenantId, UUID id) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertProgressRecordable(g);
        g.setStatus(GoalStatus.UNDER_REVIEW);
        publish(GoalEventType.GOAL_UNDER_REVIEW, g, null);
        return mapper.toResponse(g);
    }

    public GoalResponse review(UUID tenantId, UUID id, GoalReviewRequest req) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertReviewable(g);
        g.setReviewScore(req.reviewScore());
        g.setReviewNotes(req.notes());
        g.setReviewedAt(Instant.now());
        g.setReviewedBy(GoalSecurity.currentActor());
        publish(GoalEventType.GOAL_REVIEWED, g, req.reviewScore().toPlainString());
        return mapper.toResponse(g);
    }

    public GoalResponse complete(UUID tenantId, UUID id) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertClosable(g);
        g.setStatus(GoalStatus.COMPLETED);
        g.setClosedAt(Instant.now());
        g.setClosedBy(GoalSecurity.currentActor());
        publish(GoalEventType.GOAL_COMPLETED, g, null);
        return mapper.toResponse(g);
    }

    public GoalResponse cancel(UUID tenantId, UUID id, String reason) {
        Goal g = requireGoal(tenantId, id);
        lifecycle.assertClosable(g);
        g.setStatus(GoalStatus.CANCELLED);
        g.setClosedAt(Instant.now());
        g.setClosedBy(GoalSecurity.currentActor());
        publish(GoalEventType.GOAL_CANCELLED, g, reason);
        return mapper.toResponse(g);
    }

    @Transactional(readOnly = true)
    public GoalResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(requireGoal(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> byEmployee(UUID tenantId, UUID employeeId) {
        return goals.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> byStatus(UUID tenantId, UUID companyId, GoalStatus status) {
        return goals.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> byScope(UUID tenantId, UUID companyId, GoalScope scope) {
        return goals.findAllByTenantIdAndCompanyIdAndScope(tenantId, companyId, scope).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> forCycle(UUID tenantId, UUID performanceCycleId) {
        return goals.findAllByTenantIdAndPerformanceCycleId(tenantId, performanceCycleId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GoalProgressResponse> progressHistory(UUID tenantId, UUID id) {
        return progress.findAllByTenantIdAndGoalIdOrderByRecordedAtDesc(tenantId, id).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        return new GoalDashboardResponse(
                goals.countByTenantIdAndCompanyIdAndStatus(tenantId, companyId, GoalStatus.DRAFT),
                goals.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, GoalStatus.ASSIGNED),
                goals.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, GoalStatus.IN_PROGRESS),
                goals.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, GoalStatus.UNDER_REVIEW),
                goals.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, GoalStatus.COMPLETED),
                goals.countByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, GoalStatus.CANCELLED),
                goals.findAllByTenantIdAndCompanyIdAndScope(
                                tenantId, companyId, GoalScope.INDIVIDUAL)
                        .size(),
                goals.findAllByTenantIdAndCompanyIdAndScope(tenantId, companyId, GoalScope.TEAM)
                        .size(),
                goals.findAllByTenantIdAndCompanyIdAndScope(
                                tenantId, companyId, GoalScope.DEPARTMENT)
                        .size(),
                goals.findAllByTenantIdAndCompanyIdAndScope(tenantId, companyId, GoalScope.COMPANY)
                        .size());
    }

    @Transactional(readOnly = true)
    public List<GoalReportRowResponse> reportByStatus(
            UUID tenantId, UUID companyId, GoalStatus status) {
        return goals.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toReportRow)
                .toList();
    }

    private Goal requireGoal(UUID tenantId, UUID id) {
        return goals.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Goal not found"));
    }

    private void publish(GoalEventType type, Goal g, String detail) {
        events.publishEvent(
                new GoalEvent(
                        type,
                        g.getTenantId(),
                        g.getCompanyId(),
                        g.getId(),
                        g.getLibraryGoal() == null ? null : g.getLibraryGoal().getId(),
                        g.getEmployee() == null ? null : g.getEmployee().getId(),
                        g.getOrgUnitId(),
                        detail,
                        GoalSecurity.currentActor(),
                        Instant.now()));
    }
}
