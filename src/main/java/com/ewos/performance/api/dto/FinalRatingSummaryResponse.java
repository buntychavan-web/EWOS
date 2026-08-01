package com.ewos.performance.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record FinalRatingSummaryResponse(
        long totalFinalised,
        List<BandCount> byBand,
        Map<String, Long> byIncrementRecommendation,
        Map<String, Long> byPromotionRecommendation) {

    public record BandCount(String band, long count, BigDecimal averageRating) {}
}
