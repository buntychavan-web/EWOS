package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class LopCalculatorTest {

    private final LopCalculator calc = new LopCalculator();

    @Test
    void weekdaysBetweenSpansMonAndSunAsFive() {
        // Mon 2026-01-05 through Sun 2026-01-11.
        assertThat(calc.weekdaysBetween(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11)))
                .isEqualByComparingTo("5");
    }

    @Test
    void lopClipsToPeriodBoundaries() {
        // Unpaid leave 2026-01-01 to 2026-01-31 but period is only 2026-01-15 to 2026-01-19.
        // Jan 15 (Thu), 16 (Fri), 17 (Sat), 18 (Sun), 19 (Mon) → 3 weekdays.
        LeaveRequest r = leave(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        BigDecimal lop =
                calc.computeLopDays(
                        List.of(r), LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 19));
        assertThat(lop).isEqualByComparingTo("3");
    }

    @Test
    void multipleOverlappingRangesAreSummed() {
        // Two separate unpaid leave rows: Mon-Tue and Thu-Fri.
        LeaveRequest a = leave(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3));
        LeaveRequest b = leave(LocalDate.of(2026, 2, 5), LocalDate.of(2026, 2, 6));
        BigDecimal lop =
                calc.computeLopDays(
                        List.of(a, b), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(lop).isEqualByComparingTo("4");
    }

    @Test
    void weekendDaysAreExcluded() {
        LeaveRequest weekend = leave(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11));
        BigDecimal lop =
                calc.computeLopDays(
                        List.of(weekend), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(lop).isEqualByComparingTo("0");
    }

    @Test
    void effectiveBasicScalesByWorkedRatio() {
        BigDecimal basic = new BigDecimal("22000.00");
        BigDecimal workingDays = new BigDecimal("22");
        BigDecimal lopDays = new BigDecimal("2");
        // 22000 * (22 - 2) / 22 = 20000.00
        assertThat(calc.effectiveBasic(basic, workingDays, lopDays))
                .isEqualByComparingTo("20000.00");
    }

    @Test
    void zeroLopReturnsBasicUnchanged() {
        BigDecimal basic = new BigDecimal("50000.00");
        assertThat(calc.effectiveBasic(basic, new BigDecimal("22"), BigDecimal.ZERO))
                .isEqualByComparingTo("50000.00");
    }

    @Test
    void fullMonthLopFloorsToZero() {
        BigDecimal basic = new BigDecimal("22000.00");
        BigDecimal workingDays = new BigDecimal("22");
        BigDecimal lopDays = new BigDecimal("30");
        assertThat(calc.effectiveBasic(basic, workingDays, lopDays)).isEqualByComparingTo("0.00");
    }

    private static LeaveRequest leave(LocalDate start, LocalDate end) {
        LeaveRequest r = new LeaveRequest();
        r.setStartDate(start);
        r.setEndDate(end);
        LeaveType t = new LeaveType();
        t.setPaid(false);
        r.setLeaveType(t);
        return r;
    }
}
