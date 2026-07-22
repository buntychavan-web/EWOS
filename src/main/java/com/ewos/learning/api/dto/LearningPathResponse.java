package com.ewos.learning.api.dto;

import java.util.UUID;

public record LearningPathResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        boolean active) {}
