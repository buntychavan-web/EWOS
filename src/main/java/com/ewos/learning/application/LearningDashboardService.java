package com.ewos.learning.application;

import com.ewos.learning.api.dto.LearningDashboardResponse;
import com.ewos.learning.domain.EnrollmentStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LearningDashboardService {

    private final CourseCatalogueService courses;
    private final TrainingSessionService sessions;
    private final EnrollmentService enrollments;
    private final CertificationService certifications;

    public LearningDashboardService(
            CourseCatalogueService courses,
            TrainingSessionService sessions,
            EnrollmentService enrollments,
            CertificationService certifications) {
        this.courses = courses;
        this.sessions = sessions;
        this.enrollments = enrollments;
        this.certifications = certifications;
    }

    public LearningDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        return new LearningDashboardResponse(
                courses.activeCount(tenantId, companyId),
                sessions.scheduledCount(tenantId, companyId),
                enrollments.countByStatus(tenantId, companyId, EnrollmentStatus.NOMINATED),
                enrollments.countByStatus(tenantId, companyId, EnrollmentStatus.ENROLLED),
                enrollments.countByStatus(tenantId, companyId, EnrollmentStatus.IN_PROGRESS),
                enrollments.countByStatus(tenantId, companyId, EnrollmentStatus.COMPLETED),
                enrollments.countByStatus(tenantId, companyId, EnrollmentStatus.WITHDRAWN),
                certifications.activeCount(tenantId, companyId),
                certifications.expiringWithin30Days(tenantId, companyId));
    }
}
