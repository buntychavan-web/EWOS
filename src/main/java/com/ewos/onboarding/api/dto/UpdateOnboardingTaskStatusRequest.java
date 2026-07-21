package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingTaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOnboardingTaskStatusRequest(
        @NotNull OnboardingTaskStatus status, @Size(max = 4000) String notes, String resultJson) {}
