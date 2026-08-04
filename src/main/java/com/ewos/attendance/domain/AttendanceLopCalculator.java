package com.ewos.attendance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Sprint 24L item 3 — classifies every day of a payroll period for one employee into a {@link
 * DailyAttendanceStatus} from their raw {@link TimeEntry} events, then sums the LOP-triggering days
 * into a loss-of-pay figure. Reuses {@link TimesheetCalculator}'s tested IN/OUT/BREAK pairing
 * algorithm one day at a time (period-of-one-day) rather than reimplementing it.
 *
 * <p>This result is additive on top of the existing leave-driven {@code
 * com.ewos.payroll.domain.LopCalculator}: days covered by an approved leave request are classified
 * {@link DailyAttendanceStatus#ON_LEAVE} and contribute zero here, because the leave module already
 * accounts for them. This calculator only surfaces LOP for days with no approved leave at all —
 * unexplained absences, half-days, and missing punches that raw attendance data reveals but the
 * leave-only calculation could not see.
 */
@Component
public final class AttendanceLopCalculator {

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final TimesheetCalculator timesheetCalculator;

    public AttendanceLopCalculator(TimesheetCalculator timesheetCalculator) {
        this.timesheetCalculator = timesheetCalculator;
    }

    /** One classified day plus the LOP weight (0, 0.5, or 1.0) it contributes. */
    public record DayRecord(LocalDate date, DailyAttendanceStatus status, BigDecimal lopWeight) {}

    /** The full period breakdown plus its summed LOP-days figure. */
    public record Result(BigDecimal lopDays, List<DayRecord> days) {}

    public Result compute(
            AttendancePolicy policy,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<TimeEntry> employeeEntries,
            Set<LocalDate> holidayDates,
            Set<LocalDate> leaveDates) {
        List<DayRecord> days = new ArrayList<>();
        LocalDate cursor = periodStart;
        while (!cursor.isAfter(periodEnd)) {
            days.add(classifyDay(policy, cursor, employeeEntries, holidayDates, leaveDates));
            cursor = cursor.plusDays(1);
        }

        List<DayRecord> withSandwich =
                policy.isSandwichLeavePolicyEnabled() ? applySandwichPolicy(days) : days;

        BigDecimal lopDays =
                withSandwich.stream()
                        .map(DayRecord::lopWeight)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
        return new Result(lopDays, withSandwich);
    }

    private DayRecord classifyDay(
            AttendancePolicy policy,
            LocalDate date,
            List<TimeEntry> employeeEntries,
            Set<LocalDate> holidayDates,
            Set<LocalDate> leaveDates) {
        if (!isWorkingDay(policy, date)) {
            return new DayRecord(date, DailyAttendanceStatus.WEEKLY_OFF, BigDecimal.ZERO);
        }
        if (holidayDates.contains(date)) {
            return new DayRecord(date, DailyAttendanceStatus.HOLIDAY, BigDecimal.ZERO);
        }
        if (leaveDates.contains(date)) {
            return new DayRecord(date, DailyAttendanceStatus.ON_LEAVE, BigDecimal.ZERO);
        }

        List<TimeEntry> dayEntries = entriesOn(date, employeeEntries);
        if (dayEntries.isEmpty()) {
            return new DayRecord(date, DailyAttendanceStatus.ABSENT, BigDecimal.ONE);
        }

        if (hasOpenPunch(dayEntries)) {
            return new DayRecord(date, DailyAttendanceStatus.MISSING_PUNCH, BigDecimal.ONE);
        }

        BigDecimal workedHours =
                timesheetCalculator.calculate(policy, date, date, dayEntries).workedHours();
        BigDecimal standard = policy.getStandardHoursPerDay();
        BigDecimal halfStandard = standard.divide(TWO, 2, RoundingMode.HALF_UP);
        if (workedHours.compareTo(standard) >= 0) {
            return new DayRecord(date, DailyAttendanceStatus.PRESENT, BigDecimal.ZERO);
        }
        if (workedHours.compareTo(halfStandard) >= 0) {
            return new DayRecord(date, DailyAttendanceStatus.HALF_DAY, HALF);
        }
        return new DayRecord(date, DailyAttendanceStatus.ABSENT, BigDecimal.ONE);
    }

    private static List<DayRecord> applySandwichPolicy(List<DayRecord> days) {
        List<DayRecord> result = new ArrayList<>(days);
        int i = 0;
        while (i < result.size()) {
            if (!isOffDay(result.get(i).status())) {
                i++;
                continue;
            }
            int runStart = i;
            int runEnd = i;
            while (runEnd + 1 < result.size() && isOffDay(result.get(runEnd + 1).status())) {
                runEnd++;
            }
            boolean hasPredecessor = runStart > 0;
            boolean hasSuccessor = runEnd + 1 < result.size();
            boolean flankedByLop =
                    hasPredecessor
                            && hasSuccessor
                            && isLopDay(result.get(runStart - 1).status())
                            && isLopDay(result.get(runEnd + 1).status());
            if (flankedByLop) {
                for (int j = runStart; j <= runEnd; j++) {
                    result.set(
                            j,
                            new DayRecord(
                                    result.get(j).date(),
                                    DailyAttendanceStatus.SANDWICHED_LOP,
                                    BigDecimal.ONE));
                }
            }
            i = runEnd + 1;
        }
        return result;
    }

    private static boolean isOffDay(DailyAttendanceStatus status) {
        return status == DailyAttendanceStatus.WEEKLY_OFF
                || status == DailyAttendanceStatus.HOLIDAY;
    }

    private static boolean isLopDay(DailyAttendanceStatus status) {
        return status == DailyAttendanceStatus.ABSENT
                || status == DailyAttendanceStatus.HALF_DAY
                || status == DailyAttendanceStatus.MISSING_PUNCH
                || status == DailyAttendanceStatus.SANDWICHED_LOP;
    }

    private static boolean isWorkingDay(AttendancePolicy policy, LocalDate date) {
        String working = policy.getWorkingDays().toUpperCase(Locale.ROOT);
        String dayCode = date.getDayOfWeek().name().substring(0, 3);
        return working.contains(dayCode);
    }

    private static List<TimeEntry> entriesOn(LocalDate date, List<TimeEntry> entries) {
        var from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var to = date.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return entries.stream()
                .filter(e -> !e.getOccurredAt().isBefore(from) && !e.getOccurredAt().isAfter(to))
                .sorted(Comparator.comparing(TimeEntry::getOccurredAt))
                .collect(Collectors.toList());
    }

    private static boolean hasOpenPunch(List<TimeEntry> dayEntries) {
        long ins = dayEntries.stream().filter(e -> e.getEventType() == TimeEventType.IN).count();
        long outs = dayEntries.stream().filter(e -> e.getEventType() == TimeEventType.OUT).count();
        return ins > outs;
    }
}
