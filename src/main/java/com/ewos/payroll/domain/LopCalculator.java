package com.ewos.payroll.domain;

import com.ewos.leave.domain.LeaveRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes loss-of-pay (LOP) days for a payslip: intersects each approved unpaid leave request with
 * the payroll period, counts weekdays inside the overlap, and returns the sum. Paid leave rows are
 * ignored — only unpaid leave reduces gross.
 */
@Component
public final class LopCalculator {

    /**
     * Count weekday LOP days for one employee against the period bounds.
     *
     * @param unpaidLeavesInPeriod approved leave requests whose type has {@code paid = false} and
     *     which overlap {@code [periodStart, periodEnd]}. Callers filter by payment flag; the
     *     calculator does not re-check.
     */
    public BigDecimal computeLopDays(
            List<LeaveRequest> unpaidLeavesInPeriod, LocalDate periodStart, LocalDate periodEnd) {
        if (unpaidLeavesInPeriod == null || unpaidLeavesInPeriod.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long days = 0;
        for (LeaveRequest r : unpaidLeavesInPeriod) {
            LocalDate from =
                    r.getStartDate().isBefore(periodStart) ? periodStart : r.getStartDate();
            LocalDate to = r.getEndDate().isAfter(periodEnd) ? periodEnd : r.getEndDate();
            LocalDate cursor = from;
            while (!cursor.isAfter(to)) {
                DayOfWeek dow = cursor.getDayOfWeek();
                if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                    days++;
                }
                cursor = cursor.plusDays(1);
            }
        }
        return BigDecimal.valueOf(days);
    }

    /**
     * Reduce a basic-per-period figure by the LOP-days ratio. Returns {@code basic * (workingDays -
     * lopDays) / workingDays}, rounded to 2 decimals HALF_UP. If {@code workingDays <= 0} or {@code
     * lopDays <= 0} the basic is returned unchanged.
     */
    public BigDecimal effectiveBasic(
            BigDecimal basicPerPeriod, BigDecimal workingDays, BigDecimal lopDays) {
        if (basicPerPeriod == null) {
            return BigDecimal.ZERO;
        }
        if (workingDays == null || workingDays.signum() <= 0) {
            return basicPerPeriod.setScale(2, RoundingMode.HALF_UP);
        }
        if (lopDays == null || lopDays.signum() <= 0) {
            return basicPerPeriod.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal worked = workingDays.subtract(lopDays).max(BigDecimal.ZERO);
        return basicPerPeriod.multiply(worked).divide(workingDays, 2, RoundingMode.HALF_UP);
    }

    /** Weekday count between two dates inclusive; used as the working-days denominator. */
    public BigDecimal weekdaysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return BigDecimal.ZERO;
        }
        long days = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days++;
            }
            cursor = cursor.plusDays(1);
        }
        return BigDecimal.valueOf(days);
    }
}
