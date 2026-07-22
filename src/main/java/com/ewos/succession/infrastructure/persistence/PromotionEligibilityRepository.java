package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.PromotionEligibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionEligibilityRepository extends JpaRepository<PromotionEligibility, UUID> {

    Optional<PromotionEligibility> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PromotionEligibility> findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(
            UUID tenantId, UUID employeeId);

    long countByTenantIdAndCompanyIdAndEligibleTrue(UUID tenantId, UUID companyId);
}
