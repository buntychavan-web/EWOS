package com.ewos.performance.api.dto;

import java.util.UUID;

public record AppraisalTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        int ratingScaleMin,
        int ratingScaleMax,
        boolean active) {}
