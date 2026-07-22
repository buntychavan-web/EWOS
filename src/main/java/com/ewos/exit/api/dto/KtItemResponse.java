package com.ewos.exit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record KtItemResponse(
        UUID id,
        UUID tenantId,
        UUID resignationId,
        String topic,
        String description,
        UUID transferredTo,
        boolean completed,
        Instant completedAt,
        UUID completedBy,
        String notes) {}
