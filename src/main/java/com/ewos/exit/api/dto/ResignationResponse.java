package com.ewos.exit.api.dto;

import com.ewos.exit.domain.RehireEligibility;
import com.ewos.exit.domain.ResignationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ResignationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        Instant submittedAt,
        UUID submittedBy,
        LocalDate intendedLastDay,
        String reason,
        int noticePeriodDays,
        LocalDate noticeStartDate,
        LocalDate noticeEndDate,
        Integer buyoutDays,
        BigDecimal buyoutAmount,
        Instant acceptedAt,
        UUID acceptedBy,
        UUID exitWorkflowInstanceId,
        ResignationStatus status,
        LocalDate actualLastDay,
        RehireEligibility rehireEligibility,
        String rehireNotes) {}
