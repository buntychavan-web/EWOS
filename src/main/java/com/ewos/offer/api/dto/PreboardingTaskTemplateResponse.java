package com.ewos.offer.api.dto;

import com.ewos.offer.domain.preboarding.PreboardingTaskOwner;
import com.ewos.offer.domain.preboarding.PreboardingTaskType;
import java.util.UUID;

public record PreboardingTaskTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        PreboardingTaskType taskType,
        int sortOrder,
        boolean mandatory,
        PreboardingTaskOwner defaultOwner,
        Integer defaultSlaDays,
        boolean active,
        long versionNo) {}
