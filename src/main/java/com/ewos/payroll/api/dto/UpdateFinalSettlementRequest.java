package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFinalSettlementRequest(
        LocalDate terminationDate,
        LocalDate lastWorkingDate,
        @DecimalMin("0.00") BigDecimal unusedLeaveDays,
        @DecimalMin("0.0000") BigDecimal encashmentAmount,
        @DecimalMin("0.0000") BigDecimal gratuityAmount,
        @DecimalMin("0.0000") BigDecimal noticePayRecovery,
        @DecimalMin("0.0000") BigDecimal noticePayReceivable,
        @DecimalMin("0.0000") BigDecimal otherEarnings,
        @DecimalMin("0.0000") BigDecimal otherDeductions,
        @Size(max = 2048) String notes) {}
