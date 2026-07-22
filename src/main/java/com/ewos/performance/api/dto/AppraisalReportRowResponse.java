package com.ewos.performance.api.dto;

import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.IncrementRecommendation;
import com.ewos.performance.domain.PromotionRecommendation;
import java.math.BigDecimal;
import java.util.UUID;

public record AppraisalReportRowResponse(
        UUID appraisalId,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        AppraisalStatus status,
        BigDecimal selfRating,
        BigDecimal managerRating,
        BigDecimal reviewerRating,
        BigDecimal calibratedRating,
        BigDecimal finalRating,
        String finalBand,
        IncrementRecommendation incrementRecommendation,
        PromotionRecommendation promotionRecommendation) {}
