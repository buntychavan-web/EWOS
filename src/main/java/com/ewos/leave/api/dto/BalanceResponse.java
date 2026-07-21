package com.ewos.leave.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BalanceResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID leaveTypeId,
        String leaveTypeCode,
        int year,
        BigDecimal accruedDays,
        BigDecimal consumedDays,
        BigDecimal pendingDays,
        BigDecimal adjustmentDays,
        BigDecimal carryForwardDays,
        BigDecimal availableDays,
        Instant createdAt,
        Instant updatedAt,
        long versionNo) {}
