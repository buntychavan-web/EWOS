package com.ewos.dashboard.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregate counts for the platform dashboard header")
public record DashboardSummaryResponse(
        @Schema(description = "Total non-deleted employees; 0 until Employment module ships")
                long employees,
        @Schema(description = "Total non-deleted users") long users,
        @Schema(description = "Total non-deleted departments; 0 until a Department entity exists")
                long departments,
        @Schema(description = "Total non-deleted roles") long roles) {}
