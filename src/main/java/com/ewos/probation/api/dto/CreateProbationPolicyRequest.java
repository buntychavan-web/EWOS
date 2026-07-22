package com.ewos.probation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateProbationPolicyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @Min(1) int defaultPeriodDays,
        @Min(0) int maxExtensionDays,
        boolean allowEarlyConfirm) {}
