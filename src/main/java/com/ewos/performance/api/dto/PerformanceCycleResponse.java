package com.ewos.performance.api.dto;

import com.ewos.performance.domain.PerformanceCycleStatus;
import java.time.LocalDate;
import java.util.UUID;

public record PerformanceCycleResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        LocalDate periodStart,
        LocalDate periodEnd,
        PerformanceCycleStatus status,
        LocalDate selfAssessmentDue,
        LocalDate managerAssessmentDue,
        LocalDate reviewerAssessmentDue,
        LocalDate calibrationDue,
        boolean bellCurveEnabled,
        String bellCurveConfigJson) {}
