package com.ewos.onboarding.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateOnboardingPlanRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID sourceOfferId,
        UUID sourceChecklistId,
        LocalDate joiningDate,
        UUID managerEmployeeId,
        UUID buddyEmployeeId,
        @Size(max = 4000) String notes) {}
