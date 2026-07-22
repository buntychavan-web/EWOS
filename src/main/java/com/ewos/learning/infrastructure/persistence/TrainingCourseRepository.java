package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.TrainingCourse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingCourseRepository extends JpaRepository<TrainingCourse, UUID> {

    Optional<TrainingCourse> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<TrainingCourse> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
