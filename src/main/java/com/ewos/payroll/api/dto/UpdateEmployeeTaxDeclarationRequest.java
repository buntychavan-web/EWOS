package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateEmployeeTaxDeclarationRequest(
        TaxRegime regime,
        @DecimalMin("0.00") BigDecimal previousEmployerIncome,
        @DecimalMin("0.00") BigDecimal otherIncome,
        @DecimalMin("0.00") BigDecimal housePropertyLoss,
        @DecimalMin("0.00") BigDecimal chapterViaDeclaredAmount,
        @DecimalMin("0.00") BigDecimal rentPaidAnnual,
        Boolean metroCity,
        @DecimalMin("0.00") BigDecimal ltaExemptionDeclared) {}
