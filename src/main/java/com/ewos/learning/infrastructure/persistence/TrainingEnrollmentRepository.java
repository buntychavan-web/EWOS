package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.EnrollmentStatus;
import com.ewos.learning.domain.TrainingEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, UUID> {

    Optional<TrainingEnrollment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TrainingEnrollment> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<TrainingEnrollment> findAllByTenantIdAndCourseIdAndStatus(
            UUID tenantId, UUID courseId, EnrollmentStatus status);

    List<TrainingEnrollment> findAllByTenantIdAndSessionId(UUID tenantId, UUID sessionId);

    List<TrainingEnrollment> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, EnrollmentStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, EnrollmentStatus status);
}
