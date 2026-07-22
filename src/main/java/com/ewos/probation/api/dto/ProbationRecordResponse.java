package com.ewos.probation.api.dto;

import com.ewos.probation.domain.HrRecommendation;
import com.ewos.probation.domain.ProbationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProbationRecordResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID policyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate extendedEnd,
        LocalDate effectiveEnd,
        String extensionReason,
        ProbationStatus status,
        String managerReviewNotes,
        Instant managerReviewAt,
        UUID managerReviewBy,
        HrRecommendation hrRecommendation,
        String hrRecommendationNotes,
        Instant hrRecommendedAt,
        UUID hrRecommendedBy,
        UUID approvalWorkflowInstanceId,
        Instant confirmedAt,
        UUID confirmedBy,
        String confirmationLetterUri,
        Instant terminatedAt,
        UUID terminatedBy,
        String outcomeNotes) {}
