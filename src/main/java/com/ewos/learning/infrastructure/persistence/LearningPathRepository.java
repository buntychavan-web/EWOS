package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.LearningPath;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    Optional<LearningPath> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<LearningPath> findAllByTenantIdAndCompanyIdAndActiveTrue(UUID tenantId, UUID companyId);
}
