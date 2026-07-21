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
            BigDecimal basicApplied,
            BigDecimal lopDays) {}

    /**
     * Simple compute — no LOP, no arrears. Retained for backward compatibility with WP-009 callers
     * and tests.
     */
    public ComputedPayslip compute(EmployeeCompensation compensation) {
        return compute(compensation, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    /**
     * Compute payslip lines from a compensation record with optional LOP and arrears. If {@code
     * lopDays > 0} and {@code workingDays > 0} the basic-per-period is reduced by the ratio {@code
     * (workingDays - lopDays) / workingDays} before percentage components resolve against it. Each
     * arrear is appended as an extra earning/deduction line after the standard components.
     */
    public ComputedPayslip compute(
            EmployeeCompensation compensation,
            BigDecimal lopDays,
            BigDecimal workingDays,
            List<PayrollArrear> arrears) {
        BigDecimal originalBasic = scale(compensation.getBasicSalary());
        BigDecimal basic = reduceForLop(originalBasic, workingDays, lopDays);
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

        if (arrears != null) {
            int nextOrder = 900;
            for (PayrollArrear a : arrears) {
                lines.add(arrearLine(a, nextOrder++));
            }
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
                lines,
                scale(gross),
                scale(deductions),
                net.max(BigDecimal.ZERO),
                basic,
                lopDays == null ? BigDecimal.ZERO : lopDays);
    }

    private static BigDecimal reduceForLop(
            BigDecimal basic, BigDecimal workingDays, BigDecimal lopDays) {
        if (basic == null || basic.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (workingDays == null
                || workingDays.signum() <= 0
                || lopDays == null
                || lopDays.signum() <= 0) {
            return basic;
        }
        BigDecimal worked = workingDays.subtract(lopDays).max(BigDecimal.ZERO);
        return basic.multiply(worked).divide(workingDays, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static PayslipLine arrearLine(PayrollArrear a, int sortOrder) {
        PayslipLine line = new PayslipLine();
        line.setComponentCodeSnapshot("ARREAR_" + a.getReasonCode());
        String desc = a.getDescription();
        line.setComponentNameSnapshot(
                (desc == null || desc.isBlank()) ? "Arrear: " + a.getReasonCode() : desc);
        line.setKind(a.getKind());
        line.setCalculationType(PayComponentCalculationType.FIXED);
        line.setAmount(a.getAmount() == null ? BigDecimal.ZERO : scale(a.getAmount()));
        line.setPercentageApplied(BigDecimal.ZERO);
        line.setSortOrder(sortOrder);
        return line;
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
