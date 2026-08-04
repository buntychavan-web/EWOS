package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateIncomeTaxPolicyRequest(
        @DecimalMin("0.00") BigDecimal rebateIncomeThreshold,
        @DecimalMin("0.00") BigDecimal rebateMaxAmount,
        @DecimalMin("0.00") BigDecimal cessRatePct,
        @DecimalMin("0.00") BigDecimal standardDeduction,
        @DecimalMin("0.00") BigDecimal chapterViaMaxDeduction,
        @DecimalMin("0.00") BigDecimal housePropertyLossCap,
        Boolean active) {}
