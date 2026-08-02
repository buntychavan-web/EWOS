package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;

/**
 * Employee-facing preview of the current fiscal year's projected income tax, computed with the
 * exact same {@link com.ewos.payroll.application.IncomeTaxCalculationService} the real payroll run
 * uses — never a separate, potentially-diverging estimate.
 */
public record TaxProjectionResponse(
        String fiscalYear,
        TaxRegime regime,
        BigDecimal projectedAnnualSalary,
        BigDecimal taxableIncome,
        BigDecimal hraExemption,
        BigDecimal annualTaxLiability,
        BigDecimal monthlyTdsRecovery,
        boolean basedOnLatestPayslip) {}
