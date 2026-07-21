package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.OfferTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferTemplateRepository extends JpaRepository<OfferTemplate, UUID> {

    Optional<OfferTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<OfferTemplate> findAllByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);
}
