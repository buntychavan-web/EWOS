package com.ewos.leave.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateLeaveTypeRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        Boolean paid,
        @DecimalMin("0.00") BigDecimal accrualDaysPerYear,
        @DecimalMin("0.00") BigDecimal maxBalanceDays,
        @DecimalMin("0.00") BigDecimal carryForwardDays,
        Boolean requiresApproval,
        @Min(0) Integer minNoticeDays,
        Boolean active,
        @PositiveOrZero Integer sortOrder) {}
