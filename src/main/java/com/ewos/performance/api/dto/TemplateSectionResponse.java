package com.ewos.performance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TemplateSectionResponse(
        UUID id,
        UUID templateId,
        String code,
        String name,
        String description,
        BigDecimal weightage,
        int displayOrder) {}
