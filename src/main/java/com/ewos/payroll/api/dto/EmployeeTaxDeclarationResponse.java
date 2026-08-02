package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.util.UUID;

public record EmployeeTaxDeclarationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        String fiscalYear,
        TaxRegime regime,
        BigDecimal previousEmployerIncome,
        BigDecimal otherIncome,
        BigDecimal housePropertyLoss,
        BigDecimal chapterViaDeclaredAmount,
        BigDecimal rentPaidAnnual,
        boolean metroCity,
        BigDecimal ltaExemptionDeclared,
        BigDecimal ytdTaxableSalary,
        BigDecimal ytdHraReceived,
        BigDecimal ytdTdsDeducted,
        BigDecimal ytdProfessionalTaxPaid,
        boolean active,
        long versionNo) {}
