package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row on the onboarding report — per-employee summary. */
public record OnboardingReportRowResponse(
        UUID planId,
        UUID employeeId,
        LocalDate joiningDate,
        OnboardingPlanStatus status,
        BigDecimal completionPercent,
        long totalTasks,
        long completedTasks,
        long pendingMandatoryTasks) {}
