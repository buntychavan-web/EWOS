package com.ewos.payroll.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral payroll computation. Given an employee compensation record (basic salary plus
 * component-line overrides), produces the ordered set of {@link PayslipLine} entries plus the gross
 * / deductions / net totals for one pay period. Percentage components are computed against the
 * basic salary of the compensation. Amounts are rounded to 2 decimal places using {@link
 * RoundingMode#HALF_UP}.
 *
 * <p>The calculator does not touch persistence — callers wire the resulting lines into a fresh
 * {@link Payslip} inside a transaction.
 */
@Component
public final class PayrollCalculator {

    /** Two decimal places for money — the ledger scale for every currency EWOS supports today. */
    private static final int MONEY_SCALE = 2;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** Result of computing one payslip's lines and totals. */
    public record ComputedPayslip(
            List<PayslipLine> lines,
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal net,
            BigDecimal basicApplied) {}

    /**
     * Compute payslip lines from a compensation record. Only ACTIVE components are included; a line
     * whose component is inactive is silently skipped so the catalogue can be trimmed without
     * re-editing existing compensations.
     */
    public ComputedPayslip compute(EmployeeCompensation compensation) {
        BigDecimal basic = scale(compensation.getBasicSalary());
        List<PayslipLine> lines = new ArrayList<>();

        // Basic salary is always the first line — it is the base of any percentage components.
        // Callers may register a "BASIC" component in the catalogue; if they do, we surface it as a
        // component-scoped line via the compensation line loop. Otherwise we emit an implicit
        // basic-salary earning so the payslip always shows the base.
        boolean hasExplicitBasic =
                compensation.getLines().stream()
                        .anyMatch(l -> "BASIC".equalsIgnoreCase(l.getPayComponent().getCode()));

        if (!hasExplicitBasic) {
            lines.add(implicitBasicLine(basic));
        }

        for (EmployeeCompensationLine cl : compensation.getLines()) {
            PayComponent component = cl.getPayComponent();
            if (component == null || !component.isActive()) {
                continue;
            }
            BigDecimal amount;
            BigDecimal pctApplied;
            if (component.getCalculationType() == PayComponentCalculationType.PERCENT_OF_BASIC) {
                BigDecimal pct = cl.getPercentage();
                amount =
                        scale(
                                basic.multiply(pct)
                                        .divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP));
                pctApplied = pct;
            } else {
                amount = scale(cl.getAmount());
                pctApplied = BigDecimal.ZERO;
            }
            lines.add(snapshotLine(component, amount, pctApplied));
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (PayslipLine l : lines) {
            if (l.getKind() == PayComponentKind.EARNING) {
                gross = gross.add(l.getAmount());
            } else {
                deductions = deductions.add(l.getAmount());
            }
        }
        BigDecimal net = scale(gross.subtract(deductions));
        return new ComputedPayslip(
                lines, scale(gross), scale(deductions), net.max(BigDecimal.ZERO), basic);
    }

    private static PayslipLine implicitBasicLine(BigDecimal basic) {
        PayslipLine line = new PayslipLine();
        line.setComponentCodeSnapshot("BASIC");
        line.setComponentNameSnapshot("Basic Salary");
        line.setKind(PayComponentKind.EARNING);
        line.setCalculationType(PayComponentCalculationType.FIXED);
        line.setAmount(basic);
        line.setPercentageApplied(BigDecimal.ZERO);
        line.setSortOrder(0);
        return line;
    }

    private static PayslipLine snapshotLine(
            PayComponent component, BigDecimal amount, BigDecimal pctApplied) {
        PayslipLine line = new PayslipLine();
        line.setPayComponent(component);
        line.setComponentCodeSnapshot(component.getCode());
        line.setComponentNameSnapshot(component.getName());
        line.setKind(component.getKind());
        line.setCalculationType(component.getCalculationType());
        line.setAmount(amount);
        line.setPercentageApplied(pctApplied);
        line.setSortOrder(component.getSortOrder());
        return line;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
