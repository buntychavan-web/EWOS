package com.ewos.leave.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllocationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID leaveTypeId,
        String leaveTypeCode,
        int year,
        BigDecimal allocatedDays,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
