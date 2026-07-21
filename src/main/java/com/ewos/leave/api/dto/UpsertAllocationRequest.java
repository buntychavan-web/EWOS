package com.ewos.leave.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record UpsertAllocationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull UUID leaveTypeId,
        @Min(1900) @Max(3000) int year,
        @NotNull @DecimalMin("0.00") BigDecimal allocatedDays,
        @Size(max = 1024) String notes) {}
