package com.ewos.probation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProbationPolicyRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @Min(1) int defaultPeriodDays,
        @Min(0) int maxExtensionDays,
        boolean allowEarlyConfirm,
        boolean active) {}
