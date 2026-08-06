package com.ewos.exit.api.dto;

import com.ewos.exit.domain.KtItemType;
import java.time.Instant;
import java.util.UUID;

public record KtItemResponse(
        UUID id,
        UUID tenantId,
        UUID resignationId,
        KtItemType itemType,
        String topic,
        String description,
        UUID transferredTo,
        boolean completed,
        Instant completedAt,
        UUID completedBy,
        String notes) {}
