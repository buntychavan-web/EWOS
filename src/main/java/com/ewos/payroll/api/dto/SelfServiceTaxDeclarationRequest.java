package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Same declarable fields as {@link CreateEmployeeTaxDeclarationRequest}/{@link
 * UpdateEmployeeTaxDeclarationRequest}, minus {@code tenantId}/{@code companyId}/{@code employeeId}
 * — those are resolved server-side from the authenticated caller in {@code
 * PayrollSelfServiceController}, never taken from the request body, so an employee can never
 * declare on someone else's behalf.
 */
public record SelfServiceTaxDeclarationRequest(
        @NotBlank @Size(max = 16) String fiscalYear,
        @NotNull TaxRegime regime,
        @DecimalMin("0.00") BigDecimal previousEmployerIncome,
        @DecimalMin("0.00") BigDecimal otherIncome,
        @DecimalMin("0.00") BigDecimal housePropertyLoss,
        @DecimalMin("0.00") BigDecimal chapterViaDeclaredAmount,
        @DecimalMin("0.00") BigDecimal rentPaidAnnual,
        Boolean metroCity,
        @DecimalMin("0.00") BigDecimal ltaExemptionDeclared) {}
