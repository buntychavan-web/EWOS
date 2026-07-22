package com.ewos.learning.application;

import com.ewos.learning.api.LearningMapper;
import com.ewos.learning.api.dto.AddPathCourseRequest;
import com.ewos.learning.api.dto.CreateLearningPathRequest;
import com.ewos.learning.api.dto.LearningPathResponse;
import com.ewos.learning.api.dto.PathCourseResponse;
import com.ewos.learning.domain.LearningPath;
import com.ewos.learning.domain.LearningPathCourse;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.events.LearningEvent;
import com.ewos.learning.domain.events.LearningEventType;
import com.ewos.learning.infrastructure.persistence.LearningPathCourseRepository;
import com.ewos.learning.infrastructure.persistence.LearningPathRepository;
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
public class LearningPathService {

    private final LearningPathRepository paths;
    private final LearningPathCourseRepository pathCourses;
    private final CourseCatalogueService courses;
    private final LearningMapper mapper;
    private final ApplicationEventPublisher events;

    public LearningPathService(
            LearningPathRepository paths,
            LearningPathCourseRepository pathCourses,
            CourseCatalogueService courses,
            LearningMapper mapper,
            ApplicationEventPublisher events) {
        this.paths = paths;
        this.pathCourses = pathCourses;
        this.courses = courses;
        this.mapper = mapper;
        this.events = events;
    }

    public LearningPathResponse create(CreateLearningPathRequest req) {
        if (paths.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Learning path code already exists: " + req.code());
        }
        LearningPath p = new LearningPath();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setActive(true);
        p = paths.save(p);
        publish(LearningEventType.PATH_CREATED, p);
        return mapper.toResponse(p);
    }

    public PathCourseResponse addCourse(UUID tenantId, UUID pathId, AddPathCourseRequest req) {
        LearningPath p = require(tenantId, pathId);
        TrainingCourse c = courses.require(tenantId, req.courseId());
        LearningPathCourse pc = new LearningPathCourse();
        pc.setTenantId(tenantId);
        pc.setPath(p);
        pc.setCourse(c);
        pc.setDisplayOrder(req.displayOrder());
        pc.setMandatory(req.mandatory());
        pc = pathCourses.save(pc);
        publish(LearningEventType.PATH_COURSE_ADDED, p);
        return mapper.toResponse(pc);
    }

    @Transactional(readOnly = true)
    public LearningPathResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<LearningPathResponse> listActive(UUID tenantId, UUID companyId) {
        return paths.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PathCourseResponse> listCourses(UUID tenantId, UUID pathId) {
        require(tenantId, pathId);
        return pathCourses
                .findAllByTenantIdAndPathIdOrderByDisplayOrderAsc(tenantId, pathId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    LearningPath require(UUID tenantId, UUID id) {
        return paths.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Learning path not found"));
    }

    private void publish(LearningEventType type, LearningPath p) {
        events.publishEvent(
                new LearningEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        null,
                        p.getId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        LearningSecurity.currentActor(),
                        Instant.now()));
    }
}
