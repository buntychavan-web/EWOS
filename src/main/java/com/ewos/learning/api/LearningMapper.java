package com.ewos.learning.api;

import com.ewos.learning.api.dto.CertificationResponse;
import com.ewos.learning.api.dto.CourseResponse;
import com.ewos.learning.api.dto.EnrollmentResponse;
import com.ewos.learning.api.dto.LearningPathResponse;
import com.ewos.learning.api.dto.PathCourseResponse;
import com.ewos.learning.api.dto.SessionResponse;
import com.ewos.learning.domain.Certification;
import com.ewos.learning.domain.LearningPath;
import com.ewos.learning.domain.LearningPathCourse;
import com.ewos.learning.domain.TrainingCourse;
import com.ewos.learning.domain.TrainingEnrollment;
import com.ewos.learning.domain.TrainingSession;
import org.springframework.stereotype.Component;

/** Reflection-free mapper for learning entities. */
@Component
public class LearningMapper {

    public CourseResponse toResponse(TrainingCourse c) {
        return new CourseResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.getDeliveryMode(),
                c.getProvider(),
                c.getDurationHours(),
                c.getCost(),
                c.getCurrency(),
                c.isCertificationOffered(),
                c.getCertificationValidDays(),
                c.isActive());
    }

    public LearningPathResponse toResponse(LearningPath p) {
        return new LearningPathResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.isActive());
    }

    public PathCourseResponse toResponse(LearningPathCourse pc) {
        return new PathCourseResponse(
                pc.getId(),
                pc.getPath() == null ? null : pc.getPath().getId(),
                pc.getCourse() == null ? null : pc.getCourse().getId(),
                pc.getDisplayOrder(),
                pc.isMandatory());
    }

    public SessionResponse toResponse(TrainingSession s) {
        return new SessionResponse(
                s.getId(),
                s.getTenantId(),
                s.getCompanyId(),
                s.getCourse() == null ? null : s.getCourse().getId(),
                s.getName(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getVenue(),
                s.getTrainerName(),
                s.getTrainerEmployeeId(),
                s.getCapacity(),
                s.getStatus(),
                s.getNotes());
    }

    public EnrollmentResponse toResponse(TrainingEnrollment e) {
        return new EnrollmentResponse(
                e.getId(),
                e.getTenantId(),
                e.getCompanyId(),
                e.getCourse() == null ? null : e.getCourse().getId(),
                e.getSession() == null ? null : e.getSession().getId(),
                e.getEmployee() == null ? null : e.getEmployee().getId(),
                e.getLearningPath() == null ? null : e.getLearningPath().getId(),
                e.getNominatedBy(),
                e.getNominatedAt(),
                e.getEnrolledAt(),
                e.getStartedAt(),
                e.getCompletedAt(),
                e.getWithdrawnAt(),
                e.getWithdrawalReason(),
                e.getAttendancePercent(),
                e.getAssessmentScore(),
                e.getPassed(),
                e.getStatus());
    }

    public CertificationResponse toResponse(Certification c) {
        return new CertificationResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getEmployee() == null ? null : c.getEmployee().getId(),
                c.getCourse() == null ? null : c.getCourse().getId(),
                c.getEnrollment() == null ? null : c.getEnrollment().getId(),
                c.getCertificationName(),
                c.getIssuingBody(),
                c.getReferenceNumber(),
                c.getIssuedAt(),
                c.getExpiresAt(),
                c.getStatus(),
                c.getCertificateUri(),
                c.getRevokedAt(),
                c.getRevocationReason());
    }
}
