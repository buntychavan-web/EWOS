package com.ewos.learning.application;

import com.ewos.learning.api.LearningMapper;
import com.ewos.learning.api.dto.CreateSessionRequest;
import com.ewos.learning.api.dto.SessionResponse;
import com.ewos.learning.domain.EnrollmentLifecyclePolicy;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.TrainingSession;
import com.ewos.learning.domain.TrainingSessionStatus;
import com.ewos.learning.domain.events.LearningEvent;
import com.ewos.learning.domain.events.LearningEventType;
import com.ewos.learning.infrastructure.persistence.TrainingSessionRepository;
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
public class TrainingSessionService {

    private final TrainingSessionRepository sessions;
    private final CourseCatalogueService courses;
    private final EnrollmentLifecyclePolicy lifecycle;
    private final LearningMapper mapper;
    private final ApplicationEventPublisher events;

    public TrainingSessionService(
            TrainingSessionRepository sessions,
            CourseCatalogueService courses,
            EnrollmentLifecyclePolicy lifecycle,
            LearningMapper mapper,
            ApplicationEventPublisher events) {
        this.sessions = sessions;
        this.courses = courses;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
    }

    public SessionResponse schedule(CreateSessionRequest req) {
        TrainingCourse course = courses.require(req.tenantId(), req.courseId());
        TrainingSession s = new TrainingSession();
        s.setTenantId(req.tenantId());
        s.setCompanyId(req.companyId());
        s.setCourse(course);
        s.setName(req.name());
        s.setStartsAt(req.startsAt());
        s.setEndsAt(req.endsAt());
        s.setVenue(req.venue());
        s.setTrainerName(req.trainerName());
        s.setTrainerEmployeeId(req.trainerEmployeeId());
        s.setCapacity(req.capacity());
        s.setNotes(req.notes());
        s.setStatus(TrainingSessionStatus.SCHEDULED);
        lifecycle.assertSessionScheduleValid(s);
        s = sessions.save(s);
        publish(LearningEventType.SESSION_SCHEDULED, s);
        return mapper.toResponse(s);
    }

    public SessionResponse start(UUID tenantId, UUID id) {
        TrainingSession s = require(tenantId, id);
        if (s.getStatus() != TrainingSessionStatus.SCHEDULED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only SCHEDULED sessions can be started (status=" + s.getStatus() + ")");
        }
        s.setStatus(TrainingSessionStatus.IN_PROGRESS);
        publish(LearningEventType.SESSION_STARTED, s);
        return mapper.toResponse(s);
    }

    public SessionResponse complete(UUID tenantId, UUID id) {
        TrainingSession s = require(tenantId, id);
        if (s.getStatus() != TrainingSessionStatus.IN_PROGRESS
                && s.getStatus() != TrainingSessionStatus.SCHEDULED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Session cannot be completed from status " + s.getStatus());
        }
        s.setStatus(TrainingSessionStatus.COMPLETED);
        publish(LearningEventType.SESSION_COMPLETED, s);
        return mapper.toResponse(s);
    }

    public SessionResponse cancel(UUID tenantId, UUID id, String notes) {
        TrainingSession s = require(tenantId, id);
        if (s.getStatus() == TrainingSessionStatus.COMPLETED
                || s.getStatus() == TrainingSessionStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Session is already terminal (status=" + s.getStatus() + ")");
        }
        s.setStatus(TrainingSessionStatus.CANCELLED);
        if (notes != null) {
            s.setNotes(notes);
        }
        publish(LearningEventType.SESSION_CANCELLED, s);
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> forCourse(UUID tenantId, UUID courseId) {
        return sessions.findAllByTenantIdAndCourseIdOrderByStartsAtDesc(tenantId, courseId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> byStatus(
            UUID tenantId, UUID companyId, TrainingSessionStatus status) {
        return sessions.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> calendar(UUID tenantId, UUID companyId, Instant from, Instant to) {
        return sessions.findCalendar(tenantId, companyId, from, to).stream()
                .map(mapper::toResponse)
                .toList();
    }

    long scheduledCount(UUID tenantId, UUID companyId) {
        return sessions.findAllByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, TrainingSessionStatus.SCHEDULED)
                .size();
    }

    TrainingSession require(UUID tenantId, UUID id) {
        return sessions.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Training session not found"));
    }

    private void publish(LearningEventType type, TrainingSession s) {
        events.publishEvent(
                new LearningEvent(
                        type,
                        s.getTenantId(),
                        s.getCompanyId(),
                        s.getCourse() == null ? null : s.getCourse().getId(),
                        null,
                        s.getId(),
                        null,
                        null,
                        null,
                        null,
                        LearningSecurity.currentActor(),
                        Instant.now()));
    }
}
