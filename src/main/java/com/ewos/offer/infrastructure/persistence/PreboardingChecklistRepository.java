package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.preboarding.PreboardingChecklist;
import com.ewos.offer.domain.preboarding.PreboardingChecklistStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreboardingChecklistRepository extends JpaRepository<PreboardingChecklist, UUID> {

    Optional<PreboardingChecklist> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PreboardingChecklist> findByTenantIdAndOfferId(UUID tenantId, UUID offerId);

    List<PreboardingChecklist> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, PreboardingChecklistStatus status);
}
