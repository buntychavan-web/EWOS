package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateEmployeeTaxDeclarationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 16) String fiscalYear,
        @NotNull TaxRegime regime,
        @DecimalMin("0.00") BigDecimal previousEmployerIncome,
        @DecimalMin("0.00") BigDecimal otherIncome,
        @DecimalMin("0.00") BigDecimal housePropertyLoss,
        @DecimalMin("0.00") BigDecimal chapterViaDeclaredAmount,
        @DecimalMin("0.00") BigDecimal rentPaidAnnual,
        Boolean metroCity,
        @DecimalMin("0.00") BigDecimal ltaExemptionDeclared) {}
