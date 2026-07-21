package com.ewos.onboarding.infrastructure.persistence;

import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingPlanRepository extends JpaRepository<OnboardingPlan, UUID> {

    Optional<OnboardingPlan> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<OnboardingPlan> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    Optional<OnboardingPlan> findByTenantIdAndSourceChecklistId(
            UUID tenantId, UUID sourceChecklistId);

    List<OnboardingPlan> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, OnboardingPlanStatus status);
}
