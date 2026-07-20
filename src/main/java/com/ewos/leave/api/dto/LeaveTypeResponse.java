package com.ewos.leave.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaveTypeResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        boolean paid,
        BigDecimal accrualDaysPerYear,
        BigDecimal maxBalanceDays,
        BigDecimal carryForwardDays,
        boolean requiresApproval,
        int minNoticeDays,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
