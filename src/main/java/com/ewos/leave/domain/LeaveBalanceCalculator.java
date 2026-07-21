package com.ewos.leave.domain;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral computations for leave balances and request-day counts.
 *
 * <ul>
 *   <li>{@link #availableDays(LeaveBalance)}: accrued + carryForward + adjustment − consumed −
 *       pending.
 *   <li>{@link #countLeaveDays(LocalDate, LocalDate)}: inclusive weekday count excluding
 *       Saturday/Sunday. Holidays are policy-owned and applied by the caller when a holiday
 *       calendar exists; this method is the safe minimum.
 * </ul>
 */
@Component
public final class LeaveBalanceCalculator {

    public BigDecimal availableDays(LeaveBalance balance) {
        return balance.getAccruedDays()
                .add(balance.getCarryForwardDays())
                .add(balance.getAdjustmentDays())
                .subtract(balance.getConsumedDays())
                .subtract(balance.getPendingDays());
    }

    public BigDecimal countLeaveDays(LocalDate start, LocalDate end) {
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
