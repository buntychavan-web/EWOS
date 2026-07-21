package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOnboardingTaskTemplateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @NotNull OnboardingTaskType taskType,
        @Min(0) Integer sortOrder,
        Boolean mandatory,
        OnboardingTaskOwner defaultOwner,
        @Min(0) Integer defaultSlaDays,
        Boolean active) {}
