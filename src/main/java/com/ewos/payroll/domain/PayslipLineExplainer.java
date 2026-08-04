package com.ewos.payroll.domain;

import java.math.BigDecimal;

/**
 * Turns one {@link PayslipLine}'s raw {@code kind}/{@code calculationType}/{@code
 * percentageApplied} into a short, human-readable sentence for the employee self-service payslip
 * view — "Salary Components Explanation" / "personalized payroll explanations" from Sprint 24J.
 * Pure static utility: no persistence, no config lookups, no Spring bean lifecycle needed — every
 * fact it uses is already on the line itself, so the explanation always matches exactly what was
 * actually calculated.
 */
public final class PayslipLineExplainer {

    private PayslipLineExplainer() {}

    public static String explain(
            String componentName,
            PayComponentKind kind,
            PayComponentCalculationType calculationType,
            BigDecimal percentageApplied) {
        String verb = kind == PayComponentKind.DEDUCTION ? "Deducted" : "Paid";
        return switch (calculationType) {
            case PERCENT_OF_BASIC ->
                    verb
                            + " as "
                            + trimPercent(percentageApplied)
                            + "% of your basic salary this period.";
            case STATUTORY_PF ->
                    "Provident Fund contribution, calculated per your PF configuration.";
            case STATUTORY_ESI ->
                    "Employees' State Insurance contribution, calculated per ESI rules.";
            case STATUTORY_PT -> "Professional Tax, per your state's slab rates.";
            case STATUTORY_LWF -> "Labour Welfare Fund contribution, per your state's rules.";
            case STATUTORY_TDS ->
                    "Income tax (TDS) recovered against your projected annual liability.";
            case FIXED ->
                    "A fixed "
                            + (kind == PayComponentKind.DEDUCTION ? "deduction" : "amount")
                            + " configured for "
                            + componentName
                            + ".";
        };
    }

    private static String trimPercent(BigDecimal percentageApplied) {
        if (percentageApplied == null) {
            return "0";
        }
        return percentageApplied.stripTrailingZeros().toPlainString();
    }
}
