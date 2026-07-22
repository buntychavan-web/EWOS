package com.ewos.learning.application;

import com.ewos.learning.api.LearningMapper;
import com.ewos.learning.api.dto.CourseResponse;
import com.ewos.learning.api.dto.CreateCourseRequest;
import com.ewos.learning.api.dto.UpdateCourseRequest;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.events.LearningEvent;
import com.ewos.learning.domain.events.LearningEventType;
import com.ewos.learning.infrastructure.persistence.TrainingCourseRepository;
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
public class CourseCatalogueService {

    private final TrainingCourseRepository courses;
    private final LearningMapper mapper;
    private final ApplicationEventPublisher events;

    public CourseCatalogueService(
            TrainingCourseRepository courses,
            LearningMapper mapper,
            ApplicationEventPublisher events) {
        this.courses = courses;
        this.mapper = mapper;
        this.events = events;
    }

    public CourseResponse create(CreateCourseRequest req) {
        if (courses.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Course code already exists: " + req.code());
        }
        TrainingCourse c = new TrainingCourse();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setDeliveryMode(req.deliveryMode());
        c.setProvider(req.provider());
        c.setDurationHours(req.durationHours());
        c.setCost(req.cost());
        c.setCurrency(req.currency());
        c.setCertificationOffered(req.certificationOffered());
        c.setCertificationValidDays(req.certificationValidDays());
        c.setActive(true);
        c = courses.save(c);
        publish(LearningEventType.COURSE_CREATED, c);
        return mapper.toResponse(c);
    }

    public CourseResponse update(UUID tenantId, UUID id, UpdateCourseRequest req) {
        TrainingCourse c = require(tenantId, id);
        boolean deactivating = c.isActive() && !req.active();
        c.setName(req.name());
        c.setDescription(req.description());
        if (req.deliveryMode() != null) {
            c.setDeliveryMode(req.deliveryMode());
        }
        c.setProvider(req.provider());
        c.setDurationHours(req.durationHours());
        c.setCost(req.cost());
        c.setCurrency(req.currency());
        c.setCertificationOffered(req.certificationOffered());
        c.setCertificationValidDays(req.certificationValidDays());
        c.setActive(req.active());
        publish(
                deactivating
                        ? LearningEventType.COURSE_DEACTIVATED
                        : LearningEventType.COURSE_UPDATED,
                c);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public CourseResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listActive(UUID tenantId, UUID companyId) {
        return courses.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    TrainingCourse require(UUID tenantId, UUID id) {
        return courses.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Training course not found"));
    }

    long activeCount(UUID tenantId, UUID companyId) {
        return courses.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).size();
    }

    private void publish(LearningEventType type, TrainingCourse c) {
        events.publishEvent(
                new LearningEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        LearningSecurity.currentActor(),
                        Instant.now()));
    }
}
