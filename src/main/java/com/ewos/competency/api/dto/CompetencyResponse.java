package com.ewos.competency.api.dto;

import java.util.UUID;

public record CompetencyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        String category,
        int scaleMin,
        int scaleMax,
        boolean active) {}
