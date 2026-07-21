package com.ewos.leave.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeaveBalanceCalculatorTest {

    private final LeaveBalanceCalculator calc = new LeaveBalanceCalculator();

    @Test
    void availableIsAccruedPlusCarryPlusAdjustMinusConsumedMinusPending() {
        LeaveBalance b = new LeaveBalance();
        b.setAccruedDays(new BigDecimal("20.00"));
        b.setCarryForwardDays(new BigDecimal("5.00"));
        b.setAdjustmentDays(new BigDecimal("1.00"));
        b.setConsumedDays(new BigDecimal("7.00"));
        b.setPendingDays(new BigDecimal("2.00"));

        assertThat(calc.availableDays(b)).isEqualByComparingTo("17.00");
    }

    @Test
    void weekdaysOnlyCountedInRange() {
        // Mon 2026-01-05 through Sun 2026-01-11 = 5 weekdays.
        BigDecimal days = calc.countLeaveDays(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11));
        assertThat(days).isEqualByComparingTo("5");
    }

    @Test
    void singleWeekendDayCountsAsZero() {
        assertThat(calc.countLeaveDays(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10)))
                .isEqualByComparingTo("0");
    }

    @Test
    void reverseRangeYieldsZero() {
        assertThat(calc.countLeaveDays(LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 5)))
                .isEqualByComparingTo("0");
    }

    @Test
    void nullRangeYieldsZero() {
        assertThat(calc.countLeaveDays(null, LocalDate.of(2026, 1, 5))).isEqualByComparingTo("0");
        assertThat(calc.countLeaveDays(LocalDate.of(2026, 1, 5), null)).isEqualByComparingTo("0");
    }
}
