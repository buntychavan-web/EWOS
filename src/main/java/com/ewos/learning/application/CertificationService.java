package com.ewos.learning.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.learning.api.LearningMapper;
import com.ewos.learning.api.dto.CertificationResponse;
import com.ewos.learning.api.dto.IssueCertificationRequest;
import com.ewos.learning.api.dto.RevokeCertificationRequest;
import com.ewos.learning.domain.Certification;
import com.ewos.learning.domain.CertificationStatus;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.TrainingEnrollment;
import com.ewos.learning.domain.events.LearningEvent;
import com.ewos.learning.domain.events.LearningEventType;
import com.ewos.learning.infrastructure.persistence.CertificationRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CertificationService {

    private final CertificationRepository certifications;
    private final CourseCatalogueService courses;
    private final EnrollmentService enrollments;
    private final EmployeeRepository employees;
    private final LearningMapper mapper;
    private final ApplicationEventPublisher events;

    public CertificationService(
            CertificationRepository certifications,
            CourseCatalogueService courses,
            EnrollmentService enrollments,
            EmployeeRepository employees,
            LearningMapper mapper,
            ApplicationEventPublisher events) {
        this.certifications = certifications;
        this.courses = courses;
        this.enrollments = enrollments;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    public CertificationResponse issue(IssueCertificationRequest req) {
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        TrainingCourse course =
                req.courseId() == null ? null : courses.require(req.tenantId(), req.courseId());
        TrainingEnrollment enrollment =
                req.enrollmentId() == null
                        ? null
                        : enrollments.require(req.tenantId(), req.enrollmentId());

        Certification c = new Certification();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setEmployee(employee);
        c.setCourse(course);
        c.setEnrollment(enrollment);
        c.setCertificationName(req.certificationName());
        c.setIssuingBody(req.issuingBody());
        c.setReferenceNumber(req.referenceNumber());
        c.setIssuedAt(req.issuedAt());
        c.setExpiresAt(req.expiresAt());
        c.setCertificateUri(req.certificateUri());
        c.setStatus(CertificationStatus.ACTIVE);
        c = certifications.save(c);
        publish(LearningEventType.CERTIFICATION_ISSUED, c);
        return mapper.toResponse(c);
    }

    public CertificationResponse revoke(UUID tenantId, UUID id, RevokeCertificationRequest req) {
        Certification c = require(tenantId, id);
        if (c.getStatus() == CertificationStatus.REVOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "Certification already revoked");
        }
        c.setStatus(CertificationStatus.REVOKED);
        c.setRevokedAt(Instant.now());
        c.setRevocationReason(req.reason());
        publish(LearningEventType.CERTIFICATION_REVOKED, c);
        return mapper.toResponse(c);
    }

    public CertificationResponse markExpired(UUID tenantId, UUID id) {
        Certification c = require(tenantId, id);
        if (c.getStatus() != CertificationStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only ACTIVE certifications can be marked expired (status="
                            + c.getStatus()
                            + ")");
        }
        c.setStatus(CertificationStatus.EXPIRED);
        publish(LearningEventType.CERTIFICATION_EXPIRED, c);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public CertificationResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return certifications.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> byStatus(
            UUID tenantId, UUID companyId, CertificationStatus status) {
        return certifications
                .findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CertificationResponse> expiringBy(
            UUID tenantId, UUID companyId, LocalDate through) {
        return certifications.findExpiringBy(tenantId, companyId, through).stream()
                .map(mapper::toResponse)
                .toList();
    }

    long activeCount(UUID tenantId, UUID companyId) {
        return certifications
                .findAllByTenantIdAndCompanyIdAndStatus(
                        tenantId, companyId, CertificationStatus.ACTIVE)
                .size();
    }

    long expiringWithin30Days(UUID tenantId, UUID companyId) {
        return certifications
                .findExpiringBy(tenantId, companyId, LocalDate.now().plusDays(30))
                .size();
    }

    Certification require(UUID tenantId, UUID id) {
        return certifications
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Certification not found"));
    }

    private void publish(LearningEventType type, Certification c) {
        events.publishEvent(
                new LearningEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getCourse() == null ? null : c.getCourse().getId(),
                        null,
                        null,
                        c.getEnrollment() == null ? null : c.getEnrollment().getId(),
                        c.getId(),
                        c.getEmployee() == null ? null : c.getEmployee().getId(),
                        null,
                        LearningSecurity.currentActor(),
                        Instant.now()));
    }
}
