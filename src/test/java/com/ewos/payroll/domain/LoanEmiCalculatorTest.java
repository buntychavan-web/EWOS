package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoanEmiCalculatorTest {

    private final LoanEmiCalculator calculator = new LoanEmiCalculator();

    @Test
    void interestBearingScheduleAmortizesToExactlyZero() {
        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(new BigDecimal("100000"), new BigDecimal("12"), 12);

        assertThat(schedule).hasSize(12);
        assertThat(schedule.get(11).closingBalance()).isEqualByComparingTo("0");

        BigDecimal totalPrincipal =
                schedule.stream()
                        .map(LoanEmiCalculator.InstallmentPlan::principalComponent)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPrincipal).isEqualByComparingTo("100000");

        // Standard reducing-balance EMI for 100000 @ 12% p.a. over 12 months is ~8884.88.
        assertThat(schedule.get(0).emiAmount())
                .isCloseTo(
                        new BigDecimal("8884.88"),
                        org.assertj.core.data.Offset.offset(new BigDecimal("0.5")));
    }

    @Test
    void interestComponentDecreasesAsBalanceIsPaidDown() {
        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(new BigDecimal("50000"), new BigDecimal("10"), 6);

        for (int i = 1; i < schedule.size(); i++) {
            assertThat(schedule.get(i).interestComponent())
                    .isLessThan(schedule.get(i - 1).interestComponent());
        }
    }

    @Test
    void zeroInterestScheduleSplitsPrincipalEvenlyWithNoInterest() {
        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(new BigDecimal("12000"), BigDecimal.ZERO, 12);

        assertThat(schedule).hasSize(12);
        assertThat(schedule)
                .allSatisfy(plan -> assertThat(plan.interestComponent()).isEqualByComparingTo("0"));
        assertThat(schedule.get(0).emiAmount()).isEqualByComparingTo("1000");
        assertThat(schedule.get(11).closingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void nullInterestRateIsTreatedAsZero() {
        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(new BigDecimal("6000"), null, 6);

        assertThat(schedule)
                .allSatisfy(p -> assertThat(p.interestComponent()).isEqualByComparingTo("0"));
    }

    @Test
    void rejectsNonPositivePrincipal() {
        assertThatThrownBy(() -> calculator.computeSchedule(BigDecimal.ZERO, BigDecimal.TEN, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTenure() {
        assertThatThrownBy(
                        () -> calculator.computeSchedule(new BigDecimal("1000"), BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleInstallmentLoanClosesInOnePayment() {
        List<LoanEmiCalculator.InstallmentPlan> schedule =
                calculator.computeSchedule(new BigDecimal("1000"), new BigDecimal("12"), 1);

        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).closingBalance()).isEqualByComparingTo("0");
        assertThat(schedule.get(0).principalComponent()).isEqualByComparingTo("1000");
    }
}
