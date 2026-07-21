package com.ewos.onboarding.infrastructure.persistence;

import com.ewos.onboarding.domain.OnboardingTaskInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTaskInstanceRepository
        extends JpaRepository<OnboardingTaskInstance, UUID> {

    Optional<OnboardingTaskInstance> findByIdAndTenantId(UUID id, UUID tenantId);

    List<OnboardingTaskInstance> findAllByTenantIdAndPlanIdOrderBySortOrderAsc(
            UUID tenantId, UUID planId);
}
