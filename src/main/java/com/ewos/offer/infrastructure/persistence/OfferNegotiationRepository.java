package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.OfferNegotiation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferNegotiationRepository extends JpaRepository<OfferNegotiation, UUID> {

    List<OfferNegotiation> findAllByTenantIdAndOfferIdOrderBySubmittedAtDesc(
            UUID tenantId, UUID offerId);
}
