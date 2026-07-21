package com.ewos.attendance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Framework-neutral rollup calculator. Given the raw {@link TimeEntry} events for a period and the
 * effective {@link AttendancePolicy}, produces (worked, overtime, break, absence) hours.
 *
 * <p>Semantics
 *
 * <ul>
 *   <li>Chronological events are paired: IN → next OUT is a worked interval; BREAK_START → next
 *       BREAK_END is a break interval. Unpaired opening events are ignored (the shift is treated as
 *       still open) — callers should re-run after the shift is closed.
 *   <li>Break minutes overlap with worked minutes are subtracted from worked time.
 *   <li>Overtime is the excess of worked-hours over {@code policy.standardHoursPerDay ×
 *       workingDayCount(period)}.
 *   <li>Absence is the shortfall: max(0, standardExpected − worked) if any events were captured;
 *       zero otherwise (a period with zero events is not automatically an absence — leave module
 *       will file that separately).
 * </ul>
 */
@Component
public final class TimesheetCalculator {

    /** Immutable rollup produced by {@link #calculate}. */
    public record Totals(
            BigDecimal workedHours,
            BigDecimal overtimeHours,
            BigDecimal breakHours,
            BigDecimal absenceHours) {}

    @SuppressWarnings(
            "PMD.NullAssignment") // Reset shift/break pointers after pairing is intentional.
    public Totals calculate(
            AttendancePolicy policy,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<TimeEntry> entries) {
        List<TimeEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(TimeEntry::getOccurredAt));

        long workedMinutes = 0;
        long breakMinutes = 0;

        Instant shiftStart = null;
        Instant breakStart = null;

        for (TimeEntry e : sorted) {
            switch (e.getEventType()) {
                case IN -> shiftStart = shiftStart != null ? shiftStart : e.getOccurredAt();
                case OUT -> {
                    if (shiftStart != null) {
                        workedMinutes +=
                                Duration.between(shiftStart, e.getOccurredAt()).toMinutes();
                        shiftStart = null;
                    }
                }
                case BREAK_START ->
                        breakStart = breakStart != null ? breakStart : e.getOccurredAt();
                case BREAK_END -> {
                    if (breakStart != null) {
                        breakMinutes += Duration.between(breakStart, e.getOccurredAt()).toMinutes();
                        breakStart = null;
                    }
                }
                default -> {
                    // Exhaustive on enum; here to satisfy strict switch analysis if enum grows.
                }
            }
        }

        long netWorkedMinutes = Math.max(0, workedMinutes - breakMinutes);

        int workingDayCount = countWorkingDays(policy, periodStart, periodEnd);
        BigDecimal standardExpectedHours =
                policy.getStandardHoursPerDay().multiply(BigDecimal.valueOf(workingDayCount));
        BigDecimal workedHours = minutesToHours(netWorkedMinutes);
        BigDecimal breakHours = minutesToHours(breakMinutes);

        BigDecimal overtimeHours = workedHours.subtract(standardExpectedHours).max(BigDecimal.ZERO);
        BigDecimal absenceHours =
                (sorted.isEmpty()
                                ? BigDecimal.ZERO
                                : standardExpectedHours.subtract(workedHours).max(BigDecimal.ZERO))
                        .setScale(2, RoundingMode.HALF_UP);

        return new Totals(
                workedHours.setScale(2, RoundingMode.HALF_UP),
                overtimeHours.setScale(2, RoundingMode.HALF_UP),
                breakHours.setScale(2, RoundingMode.HALF_UP),
                absenceHours);
    }

    private static int countWorkingDays(
            AttendancePolicy policy, LocalDate periodStart, LocalDate periodEnd) {
        String working = policy.getWorkingDays().toUpperCase(java.util.Locale.ROOT);
        int count = 0;
        LocalDate cursor = periodStart;
        while (!cursor.isAfter(periodEnd)) {
            String dayCode = cursor.getDayOfWeek().name().substring(0, 3);
            if (working.contains(dayCode)) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    private static BigDecimal minutesToHours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
