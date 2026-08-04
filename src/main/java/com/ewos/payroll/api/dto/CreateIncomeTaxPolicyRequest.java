package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateIncomeTaxPolicyRequest(
        @NotNull UUID tenantId,
        @NotNull TaxRegime regime,
        @NotBlank @Size(max = 16) String fiscalYear,
        @DecimalMin("0.00") BigDecimal rebateIncomeThreshold,
        @DecimalMin("0.00") BigDecimal rebateMaxAmount,
        @DecimalMin("0.00") BigDecimal cessRatePct,
        @DecimalMin("0.00") BigDecimal standardDeduction,
        @DecimalMin("0.00") BigDecimal chapterViaMaxDeduction,
        @DecimalMin("0.00") BigDecimal housePropertyLossCap,
        Boolean active) {}
