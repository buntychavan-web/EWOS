package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFinalSettlementRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull LocalDate terminationDate,
        @NotNull LocalDate lastWorkingDate,
        @DecimalMin("0.00") BigDecimal unusedLeaveDays,
        @DecimalMin("0.0000") BigDecimal encashmentAmount,
        @DecimalMin("0.0000") BigDecimal gratuityAmount,
        @DecimalMin("0.0000") BigDecimal noticePayRecovery,
        @DecimalMin("0.0000") BigDecimal noticePayReceivable,
        @DecimalMin("0.0000") BigDecimal otherEarnings,
        @DecimalMin("0.0000") BigDecimal otherDeductions,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 2048) String notes) {}
