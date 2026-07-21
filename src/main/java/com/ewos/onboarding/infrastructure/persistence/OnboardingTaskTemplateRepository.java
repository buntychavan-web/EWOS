package com.ewos.onboarding.infrastructure.persistence;

import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTaskTemplateRepository
        extends JpaRepository<OnboardingTaskTemplate, UUID> {

    Optional<OnboardingTaskTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<OnboardingTaskTemplate> findAllByTenantIdAndCompanyIdOrderBySortOrderAsc(
            UUID tenantId, UUID companyId);

    List<OnboardingTaskTemplate> findAllByTenantIdAndCompanyIdAndActiveTrueOrderBySortOrderAsc(
            UUID tenantId, UUID companyId);
}
