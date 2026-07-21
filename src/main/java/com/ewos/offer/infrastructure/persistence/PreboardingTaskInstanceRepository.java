package com.ewos.offer.infrastructure.persistence;

import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreboardingTaskInstanceRepository
        extends JpaRepository<PreboardingTaskInstance, UUID> {

    Optional<PreboardingTaskInstance> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PreboardingTaskInstance> findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(
            UUID tenantId, UUID checklistId);
}
