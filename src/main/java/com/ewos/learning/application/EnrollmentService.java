package com.ewos.learning.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.learning.api.LearningMapper;
import com.ewos.learning.api.dto.AssessmentRequest;
import com.ewos.learning.api.dto.AttendanceRequest;
import com.ewos.learning.api.dto.EnrollmentResponse;
import com.ewos.learning.api.dto.NominateRequest;
import com.ewos.learning.api.dto.WithdrawRequest;
import com.ewos.learning.domain.EnrollmentLifecyclePolicy;
import com.ewos.learning.domain.EnrollmentStatus;
import com.ewos.learning.domain.LearningPath;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.TrainingEnrollment;
import com.ewos.learning.domain.TrainingSession;
import com.ewos.learning.domain.events.LearningEvent;
import com.ewos.learning.domain.events.LearningEventType;
import com.ewos.learning.infrastructure.persistence.TrainingEnrollmentRepository;
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
public class EnrollmentService {

    private final TrainingEnrollmentRepository enrollments;
    private final CourseCatalogueService courses;
    private final TrainingSessionService sessions;
    private final LearningPathService paths;
    private final EmployeeRepository employees;
    private final EnrollmentLifecyclePolicy lifecycle;
    private final LearningMapper mapper;
    private final ApplicationEventPublisher events;

    public EnrollmentService(
            TrainingEnrollmentRepository enrollments,
            CourseCatalogueService courses,
            TrainingSessionService sessions,
            LearningPathService paths,
            EmployeeRepository employees,
            EnrollmentLifecyclePolicy lifecycle,
            LearningMapper mapper,
            ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.sessions = sessions;
        this.paths = paths;
        this.employees = employees;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.events = events;
    }

    public EnrollmentResponse nominate(NominateRequest req) {
        TrainingCourse course = courses.require(req.tenantId(), req.courseId());
        lifecycle.assertNominatable(course);
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        TrainingSession session =
                req.sessionId() == null ? null : sessions.require(req.tenantId(), req.sessionId());
        LearningPath path =
                req.learningPathId() == null
                        ? null
                        : paths.require(req.tenantId(), req.learningPathId());

        TrainingEnrollment e = new TrainingEnrollment();
        e.setTenantId(req.tenantId());
        e.setCompanyId(req.companyId());
        e.setCourse(course);
        e.setSession(session);
        e.setEmployee(employee);
        e.setLearningPath(path);
        e.setNominatedAt(Instant.now());
        e.setNominatedBy(LearningSecurity.currentActor());
        e.setStatus(EnrollmentStatus.NOMINATED);
        e = enrollments.save(e);
        publish(LearningEventType.ENROLLMENT_NOMINATED, e, null);
        return mapper.toResponse(e);
    }

    public EnrollmentResponse enroll(UUID tenantId, UUID id) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertEnrollable(e);
        e.setStatus(EnrollmentStatus.ENROLLED);
        e.setEnrolledAt(Instant.now());
        publish(LearningEventType.ENROLLMENT_ENROLLED, e, null);
        return mapper.toResponse(e);
    }

    public EnrollmentResponse start(UUID tenantId, UUID id) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertStartable(e);
        e.setStatus(EnrollmentStatus.IN_PROGRESS);
        e.setStartedAt(Instant.now());
        publish(LearningEventType.ENROLLMENT_STARTED, e, null);
        return mapper.toResponse(e);
    }

    public EnrollmentResponse recordAttendance(UUID tenantId, UUID id, AttendanceRequest req) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertAttendanceRecordable(e);
        e.setAttendancePercent(req.attendancePercent());
        if (e.getStatus() == EnrollmentStatus.ENROLLED) {
            e.setStatus(EnrollmentStatus.IN_PROGRESS);
            if (e.getStartedAt() == null) {
                e.setStartedAt(Instant.now());
            }
        }
        publish(
                LearningEventType.ENROLLMENT_ATTENDANCE_RECORDED,
                e,
                req.attendancePercent().toPlainString());
        return mapper.toResponse(e);
    }

    public EnrollmentResponse recordAssessment(UUID tenantId, UUID id, AssessmentRequest req) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertAttendanceRecordable(e);
        e.setAssessmentScore(req.score());
        e.setPassed(req.passed());
        publish(
                LearningEventType.ENROLLMENT_ASSESSMENT_RECORDED,
                e,
                req.passed() ? "PASSED" : "FAILED");
        return mapper.toResponse(e);
    }

    public EnrollmentResponse complete(UUID tenantId, UUID id) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertCompletable(e);
        Boolean passed = e.getPassed();
        if (Boolean.FALSE.equals(passed)) {
            e.setStatus(EnrollmentStatus.FAILED);
            e.setCompletedAt(Instant.now());
            publish(LearningEventType.ENROLLMENT_FAILED, e, null);
        } else {
            e.setStatus(EnrollmentStatus.COMPLETED);
            e.setCompletedAt(Instant.now());
            publish(LearningEventType.ENROLLMENT_COMPLETED, e, null);
        }
        return mapper.toResponse(e);
    }

    public EnrollmentResponse withdraw(UUID tenantId, UUID id, WithdrawRequest req) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertWithdrawable(e);
        e.setStatus(EnrollmentStatus.WITHDRAWN);
        e.setWithdrawnAt(Instant.now());
        e.setWithdrawalReason(req.reason());
        publish(LearningEventType.ENROLLMENT_WITHDRAWN, e, req.reason());
        return mapper.toResponse(e);
    }

    public EnrollmentResponse markNoShow(UUID tenantId, UUID id) {
        TrainingEnrollment e = require(tenantId, id);
        lifecycle.assertWithdrawable(e);
        e.setStatus(EnrollmentStatus.NO_SHOW);
        e.setCompletedAt(Instant.now());
        publish(LearningEventType.ENROLLMENT_NO_SHOW, e, null);
        return mapper.toResponse(e);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return enrollments.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> forSession(UUID tenantId, UUID sessionId) {
        return enrollments.findAllByTenantIdAndSessionId(tenantId, sessionId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> byStatus(
            UUID tenantId, UUID companyId, EnrollmentStatus status) {
        return enrollments
                .findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    long countByStatus(UUID tenantId, UUID companyId, EnrollmentStatus status) {
        return enrollments.countByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status);
    }

    TrainingEnrollment require(UUID tenantId, UUID id) {
        return enrollments
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Enrollment not found"));
    }

    private void publish(LearningEventType type, TrainingEnrollment e, String detail) {
        events.publishEvent(
                new LearningEvent(
                        type,
                        e.getTenantId(),
                        e.getCompanyId(),
                        e.getCourse() == null ? null : e.getCourse().getId(),
                        e.getLearningPath() == null ? null : e.getLearningPath().getId(),
                        e.getSession() == null ? null : e.getSession().getId(),
                        e.getId(),
                        null,
                        e.getEmployee() == null ? null : e.getEmployee().getId(),
                        detail,
                        LearningSecurity.currentActor(),
                        Instant.now()));
    }
}
