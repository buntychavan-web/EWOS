package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
            UUID tenantId, UUID companyId, String offerNumber);

    List<Offer> findAllByTenantIdAndApplicationIdOrderByVersionAsc(
            UUID tenantId, UUID applicationId);

    List<Offer> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, OfferStatus status);

    List<Offer> findAllByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId, OfferStatus status, Instant threshold);
}
