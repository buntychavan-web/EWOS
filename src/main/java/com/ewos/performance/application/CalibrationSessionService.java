package com.ewos.performance.application;

import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.CalibrationSessionResponse;
import com.ewos.performance.api.dto.CreateCalibrationSessionRequest;
import com.ewos.performance.domain.CalibrationSession;
import com.ewos.performance.domain.CalibrationSessionStatus;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.CalibrationSessionRepository;
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
public class CalibrationSessionService {

    private final CalibrationSessionRepository sessions;
    private final PerformanceCycleService cycles;
    private final PerformanceMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public CalibrationSessionService(
            CalibrationSessionRepository sessions,
            PerformanceCycleService cycles,
            PerformanceMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.sessions = sessions;
        this.cycles = cycles;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public CalibrationSessionResponse create(CreateCalibrationSessionRequest req) {
        guard.requireAccessForCompany(req.companyId());
        PerformanceCycle cycle = cycles.require(req.tenantId(), req.cycleId());
        CalibrationSession s = new CalibrationSession();
        s.setTenantId(req.tenantId());
        s.setCompanyId(req.companyId());
        s.setCycle(cycle);
        s.setName(req.name());
        s.setScheduledAt(req.scheduledAt());
        s.setFacilitatorId(req.facilitatorId());
        s.setNotes(req.notes());
        s.setStatus(CalibrationSessionStatus.PLANNED);
        s = sessions.save(s);
        publish(PerformanceEventType.CALIBRATION_SESSION_CREATED, s);
        return mapper.toResponse(s);
    }

    public CalibrationSessionResponse complete(UUID tenantId, UUID id, String notes) {
        CalibrationSession s = require(tenantId, id);
        if (s.getStatus() == CalibrationSessionStatus.COMPLETED
                || s.getStatus() == CalibrationSessionStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Calibration session is already terminal (" + s.getStatus() + ")");
        }
        s.setStatus(CalibrationSessionStatus.COMPLETED);
        s.setCompletedAt(Instant.now());
        if (notes != null) {
            s.setNotes(notes);
        }
        publish(PerformanceEventType.CALIBRATION_SESSION_COMPLETED, s);
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public List<CalibrationSessionResponse> listForCycle(UUID tenantId, UUID cycleId) {
        cycles.require(tenantId, cycleId);
        return sessions.findAllByTenantIdAndCycleId(tenantId, cycleId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private CalibrationSession require(UUID tenantId, UUID id) {
        CalibrationSession s =
                sessions.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Calibration session not found"));
        guard.requireAccessForCompany(s.getCompanyId());
        return s;
    }

    private void publish(PerformanceEventType type, CalibrationSession s) {
        events.publishEvent(
                new PerformanceEvent(
                        type,
                        s.getTenantId(),
                        s.getCompanyId(),
                        s.getCycle() == null ? null : s.getCycle().getId(),
                        null,
                        null,
                        null,
                        s.getId(),
                        null,
                        null,
                        PerformanceSecurity.currentActor(),
                        Instant.now()));
    }
}
