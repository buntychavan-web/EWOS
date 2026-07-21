package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreboardingTaskTemplateRepository
        extends JpaRepository<PreboardingTaskTemplate, UUID> {

    Optional<PreboardingTaskTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<PreboardingTaskTemplate> findAllByTenantIdAndCompanyIdAndActiveTrueOrderBySortOrderAsc(
            UUID tenantId, UUID companyId);

    List<PreboardingTaskTemplate> findAllByTenantIdAndCompanyIdOrderBySortOrderAsc(
            UUID tenantId, UUID companyId);
}
