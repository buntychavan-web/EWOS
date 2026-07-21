package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskType;
import java.util.UUID;

public record OnboardingTaskTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        OnboardingTaskType taskType,
        int sortOrder,
        boolean mandatory,
        OnboardingTaskOwner defaultOwner,
        Integer defaultSlaDays,
        boolean active,
        long versionNo) {}
