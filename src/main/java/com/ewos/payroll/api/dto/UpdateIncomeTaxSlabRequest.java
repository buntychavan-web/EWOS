package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateIncomeTaxSlabRequest(
        @DecimalMin("0.00") BigDecimal minIncome,
        BigDecimal maxIncome,
        @DecimalMin("0.00") BigDecimal ratePct,
        Boolean active) {}
