package com.ewos.payroll.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure, stateless EMI (Equated Monthly Installment) amortization schedule generator for {@link
 * EmployeeLoan}. Standard reducing-balance formula: {@code EMI = P * r * (1+r)^n / ((1+r)^n - 1)}
 * where {@code r} is the monthly interest rate and {@code n} the tenure in months; a zero interest
 * rate degrades to equal principal-only installments (division by zero avoided explicitly rather
 * than relying on the formula's limit).
 */
@Component
public final class LoanEmiCalculator {

    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 10;

    /** One row of the generated amortization schedule. */
    public record InstallmentPlan(
            int installmentNumber,
            BigDecimal emiAmount,
            BigDecimal principalComponent,
            BigDecimal interestComponent,
            BigDecimal closingBalance) {}

    public List<InstallmentPlan> computeSchedule(
            BigDecimal principal, BigDecimal annualInterestRatePercent, int tenureMonths) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal must be positive");
        }
        if (tenureMonths <= 0) {
            throw new IllegalArgumentException("tenureMonths must be positive");
        }
        BigDecimal rate =
                annualInterestRatePercent == null ? BigDecimal.ZERO : annualInterestRatePercent;
        if (rate.signum() == 0) {
            return zeroInterestSchedule(principal, tenureMonths);
        }
        return reducingBalanceSchedule(principal, rate, tenureMonths);
    }

    private static List<InstallmentPlan> zeroInterestSchedule(
            BigDecimal principal, int tenureMonths) {
        List<InstallmentPlan> schedule = new ArrayList<>(tenureMonths);
        BigDecimal perInstallment =
                principal.divide(BigDecimal.valueOf(tenureMonths), MONEY_SCALE, RoundingMode.DOWN);
        BigDecimal balance = principal;
        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal principalComponent = i == tenureMonths ? balance : perInstallment;
            balance = balance.subtract(principalComponent);
            schedule.add(
                    new InstallmentPlan(
                            i, principalComponent, principalComponent, BigDecimal.ZERO, balance));
        }
        return schedule;
    }

    private static List<InstallmentPlan> reducingBalanceSchedule(
            BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        BigDecimal monthlyRate =
                annualRatePercent.divide(
                        BigDecimal.valueOf(1200), RATE_SCALE, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(tenureMonths);
        BigDecimal emi =
                principal
                        .multiply(monthlyRate)
                        .multiply(onePlusRPowN)
                        .divide(
                                onePlusRPowN.subtract(BigDecimal.ONE),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP);

        List<InstallmentPlan> schedule = new ArrayList<>(tenureMonths);
        BigDecimal balance = principal;
        for (int i = 1; i <= tenureMonths; i++) {
            BigDecimal interestComponent =
                    balance.multiply(monthlyRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal principalComponent;
            BigDecimal emiThisInstallment;
            if (i == tenureMonths) {
                // Last installment closes the loan exactly, absorbing any rounding drift
                // accumulated over the schedule rather than leaving a residual balance.
                principalComponent = balance;
                emiThisInstallment = principalComponent.add(interestComponent);
            } else {
                principalComponent = emi.subtract(interestComponent);
                emiThisInstallment = emi;
            }
            balance = balance.subtract(principalComponent);
            schedule.add(
                    new InstallmentPlan(
                            i, emiThisInstallment, principalComponent, interestComponent, balance));
        }
        return schedule;
    }
}
