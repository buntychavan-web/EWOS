package com.ewos.probation.api.dto;

import com.ewos.probation.domain.HrRecommendation;
import com.ewos.probation.domain.ProbationStatus;
import java.time.LocalDate;
import java.util.UUID;

public record ProbationReportRowResponse(
        UUID recordId,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate effectiveEnd,
        ProbationStatus status,
        HrRecommendation hrRecommendation) {}
