package com.ewos.performance.api.dto;

import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.IncrementRecommendation;
import com.ewos.performance.domain.PromotionRecommendation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppraisalResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID cycleId,
        UUID templateId,
        UUID employeeId,
        UUID managerEmployeeId,
        UUID reviewerEmployeeId,
        AppraisalStatus status,
        BigDecimal selfRating,
        String selfComments,
        Instant selfSubmittedAt,
        BigDecimal managerRating,
        String managerComments,
        Instant managerSubmittedAt,
        BigDecimal reviewerRating,
        String reviewerComments,
        Instant reviewerSubmittedAt,
        BigDecimal calibratedRating,
        String calibrationNotes,
        Instant calibratedAt,
        UUID calibratedBy,
        BigDecimal finalRating,
        String finalBand,
        IncrementRecommendation incrementRecommendation,
        BigDecimal incrementPercent,
        String incrementNotes,
        PromotionRecommendation promotionRecommendation,
        String promotionNotes,
        UUID approvalWorkflowInstanceId) {}
