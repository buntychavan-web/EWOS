package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AddOnboardingTaskRequest(
        UUID templateId,
        @NotBlank @Size(max = 256) String name,
        @NotNull OnboardingTaskType taskType,
        OnboardingTaskOwner owner,
        UUID assignedEmployeeId,
        Boolean mandatory,
        @Min(0) Integer sortOrder,
        LocalDate dueDate,
        @Size(max = 4000) String notes) {}
