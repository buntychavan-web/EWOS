package com.ewos.performance.api.dto;

import java.math.BigDecimal;

public record CalibrationSummaryResponse(
        long totalCalibrated,
        long adjustedUp,
        long adjustedDown,
        long unchanged,
        BigDecimal averageDelta) {}
