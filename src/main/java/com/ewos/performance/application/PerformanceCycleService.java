package com.ewos.performance.application;

import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.CreatePerformanceCycleRequest;
import com.ewos.performance.api.dto.PerformanceCycleResponse;
import com.ewos.performance.api.dto.UpdatePerformanceCycleRequest;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.PerformanceCycleLifecyclePolicy;
import com.ewos.performance.domain.PerformanceCycleStatus;
import com.ewos.performance.domain.PerformanceCycleTransition;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.PerformanceCycleRepository;
import com.ewos.performance.infrastructure.persistence.PerformanceCycleTransitionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PerformanceCycleService {

    private final PerformanceCycleRepository cycles;
    private final PerformanceCycleTransitionRepository transitions;
    private final PerformanceCycleLifecyclePolicy lifecycle;
    private final PerformanceMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public PerformanceCycleService(
            PerformanceCycleRepository cycles,
            PerformanceCycleTransitionRepository transitions,
            PerformanceCycleLifecyclePolicy lifecycle,
            PerformanceMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.cycles = cycles;
        this.transitions = transitions;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public PerformanceCycleResponse create(CreatePerformanceCycleRequest req) {
        guard.requireAccessForCompany(req.companyId());
        if (!req.periodEnd().isAfter(req.periodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "period_end must be strictly after period_start");
        }
        if (cycles.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Performance cycle code already exists: " + req.code());
        }
        PerformanceCycle c = new PerformanceCycle();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setPeriodStart(req.periodStart());
        c.setPeriodEnd(req.periodEnd());
        c.setSelfAssessmentDue(req.selfAssessmentDue());
        c.setManagerAssessmentDue(req.managerAssessmentDue());
        c.setReviewerAssessmentDue(req.reviewerAssessmentDue());
        c.setCalibrationDue(req.calibrationDue());
        c.setBellCurveEnabled(req.bellCurveEnabled());
        c.setBellCurveConfigJson(req.bellCurveConfigJson());
        c.setStatus(PerformanceCycleStatus.DRAFT);
        c = cycles.save(c);
        publish(PerformanceEventType.CYCLE_CREATED, c, null);
        return mapper.toResponse(c);
    }

    public PerformanceCycleResponse update(
            UUID tenantId, UUID id, UpdatePerformanceCycleRequest req) {
        PerformanceCycle c = require(tenantId, id);
        c.setName(req.name());
        c.setDescription(req.description());
        c.setSelfAssessmentDue(req.selfAssessmentDue());
        c.setManagerAssessmentDue(req.managerAssessmentDue());
        c.setReviewerAssessmentDue(req.reviewerAssessmentDue());
        c.setCalibrationDue(req.calibrationDue());
        c.setBellCurveEnabled(req.bellCurveEnabled());
        c.setBellCurveConfigJson(req.bellCurveConfigJson());
        return mapper.toResponse(c);
    }

    public PerformanceCycleResponse transition(
            UUID tenantId, UUID id, PerformanceCycleStatus target, String notes) {
        PerformanceCycle c = require(tenantId, id);
        PerformanceCycleStatus from = c.getStatus();
        lifecycle.assertValidTransition(from, target);
        c.setStatus(target);

        PerformanceCycleTransition record = new PerformanceCycleTransition();
        record.setTenantId(tenantId);
        record.setCycleId(c.getId());
        record.setFromStatus(from);
        record.setToStatus(target);
        record.setNotes(notes);
        record.setTransitionedBy(PerformanceSecurity.currentActor());
        record.setTransitionedAt(Instant.now());
        transitions.save(record);

        PerformanceEventType type =
                switch (target) {
                    case CLOSED -> PerformanceEventType.CYCLE_CLOSED;
                    case CANCELLED -> PerformanceEventType.CYCLE_CANCELLED;
                    case OPEN -> PerformanceEventType.CYCLE_OPENED;
                    case RELEASED -> PerformanceEventType.CYCLE_RELEASED;
                    default -> PerformanceEventType.CYCLE_ADVANCED;
                };
        publish(type, c, target.name());
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<PerformanceCycleTransition> transitionHistory(UUID tenantId, UUID id) {
        require(tenantId, id);
        return transitions.findAllByTenantIdAndCycleIdOrderByTransitionedAtAsc(tenantId, id);
    }

    @Transactional(readOnly = true)
    public PerformanceCycleResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PerformanceCycleResponse> list(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return cycles
                .findAllByTenantIdAndCompanyIdOrderByPeriodStartDesc(tenantId, companyId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    PerformanceCycle require(UUID tenantId, UUID id) {
        PerformanceCycle c =
                cycles.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Performance cycle not found"));
        guard.requireAccessForCompany(c.getCompanyId());
        return c;
    }

    private void publish(PerformanceEventType type, PerformanceCycle c, String detail) {
        events.publishEvent(
                new PerformanceEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        detail,
                        PerformanceSecurity.currentActor(),
                        Instant.now()));
    }
}
