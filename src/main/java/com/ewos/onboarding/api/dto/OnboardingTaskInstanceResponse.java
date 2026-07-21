package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskStatus;
import com.ewos.onboarding.domain.OnboardingTaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OnboardingTaskInstanceResponse(
        UUID id,
        UUID planId,
        UUID templateId,
        String name,
        OnboardingTaskType taskType,
        OnboardingTaskOwner owner,
        UUID assignedEmployeeId,
        boolean mandatory,
        int sortOrder,
        OnboardingTaskStatus status,
        LocalDate dueDate,
        Instant startedAt,
        Instant completedAt,
        UUID completedBy,
        String externalRef,
        String resultJson,
        String notes,
        long versionNo) {}
