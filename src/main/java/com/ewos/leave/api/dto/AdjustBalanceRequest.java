package com.ewos.leave.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AdjustBalanceRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull UUID leaveTypeId,
        @Min(1900) @Max(3000) int year,
        @NotNull BigDecimal deltaDays,
        @Size(max = 1024) String reason) {}
