package com.ewos.offer.domain;

import java.math.BigDecimal;

/**
 * Structured compensation on an offer. Total CTC = base + variable + one-time + hiring + retention
 * — the domain enforces this so downstream reporting and payroll can rely on it.
 */
public record CompensationBreakdown(
        String currency,
        BigDecimal baseSalary,
        BigDecimal variablePay,
        BigDecimal oneTimeBonus,
        BigDecimal hiringBonus,
        BigDecimal retentionBonus) {

    /** Sum of components; {@code null} components count as zero. */
    public BigDecimal totalCtc() {
        return safeSum(baseSalary)
                .add(safeSum(variablePay))
                .add(safeSum(oneTimeBonus))
                .add(safeSum(hiringBonus))
                .add(safeSum(retentionBonus));
    }

    private static BigDecimal safeSum(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
