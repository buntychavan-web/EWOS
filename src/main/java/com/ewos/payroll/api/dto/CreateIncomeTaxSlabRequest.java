package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateIncomeTaxSlabRequest(
        @NotNull UUID tenantId,
        @NotNull TaxRegime regime,
        @NotBlank @Size(max = 16) String fiscalYear,
        @NotNull @DecimalMin("0.00") BigDecimal minIncome,
        BigDecimal maxIncome,
        @NotNull @DecimalMin("0.00") BigDecimal ratePct,
        Boolean active) {}
