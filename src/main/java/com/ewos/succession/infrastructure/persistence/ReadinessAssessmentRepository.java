package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.ReadinessAssessment;
import com.ewos.succession.domain.TalentTier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadinessAssessmentRepository extends JpaRepository<ReadinessAssessment, UUID> {

    Optional<ReadinessAssessment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ReadinessAssessment> findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(
            UUID tenantId, UUID employeeId);

    List<ReadinessAssessment> findAllByTenantIdAndCompanyIdAndTier(
            UUID tenantId, UUID companyId, TalentTier tier);

    long countByTenantIdAndCompanyIdAndTier(UUID tenantId, UUID companyId, TalentTier tier);
}
