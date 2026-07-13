package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Profile readiness — per-section and overall completeness percentages")
public record ReadinessResponse(
        int basicPct,
        int contactPct,
        int addressPct,
        int emergencyPct,
        int educationPct,
        int familyPct,
        int documentsPct,
        int overallPct) {}
