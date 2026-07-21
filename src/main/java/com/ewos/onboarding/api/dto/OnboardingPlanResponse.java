package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OnboardingPlanResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID sourceOfferId,
        UUID sourceChecklistId,
        LocalDate joiningDate,
        UUID managerEmployeeId,
        UUID buddyEmployeeId,
        OnboardingPlanStatus status,
        BigDecimal completionPercent,
        Instant startedAt,
        Instant completedAt,
        UUID completedBy,
        String notes,
        long versionNo) {}
