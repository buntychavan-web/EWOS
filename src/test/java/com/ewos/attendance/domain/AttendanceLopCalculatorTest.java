package com.ewos.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.attendance.domain.AttendanceLopCalculator.DayRecord;
import com.ewos.attendance.domain.AttendanceLopCalculator.Result;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttendanceLopCalculatorTest {

    private final AttendanceLopCalculator calculator =
            new AttendanceLopCalculator(new TimesheetCalculator());

    @Test
    void fullDayPresenceIsZeroLop() {
        LocalDate day = LocalDate.of(2026, 1, 5); // Monday
        List<TimeEntry> entries =
                List.of(entry(day, 9, 0, TimeEventType.IN), entry(day, 17, 0, TimeEventType.OUT));

        Result result = calculator.compute(defaultPolicy(), day, day, entries, Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("0");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.PRESENT);
    }

    @Test
    void noClockActivityOnAWorkingDayIsAbsent() {
        LocalDate day = LocalDate.of(2026, 1, 5);

        Result result =
                calculator.compute(defaultPolicy(), day, day, List.of(), Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("1");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.ABSENT);
    }

    @Test
    void halfDayWorkedYieldsHalfLop() {
        LocalDate day = LocalDate.of(2026, 1, 5);
        // 4 hours worked against an 8-hour standard day — exactly the half-day threshold.
        List<TimeEntry> entries =
                List.of(entry(day, 9, 0, TimeEventType.IN), entry(day, 13, 0, TimeEventType.OUT));

        Result result = calculator.compute(defaultPolicy(), day, day, entries, Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("0.5");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.HALF_DAY);
    }

    @Test
    void belowHalfDayThresholdIsFullAbsence() {
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries =
                List.of(entry(day, 9, 0, TimeEventType.IN), entry(day, 11, 0, TimeEventType.OUT));

        Result result = calculator.compute(defaultPolicy(), day, day, entries, Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("1");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.ABSENT);
    }

    @Test
    void openShiftWithNoOutIsMissingPunchAndCountsAsLop() {
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries = List.of(entry(day, 9, 0, TimeEventType.IN));

        Result result = calculator.compute(defaultPolicy(), day, day, entries, Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("1");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.MISSING_PUNCH);
    }

    @Test
    void weekendIsZeroLopRegardlessOfActivity() {
        LocalDate saturday = LocalDate.of(2026, 1, 10);

        Result result =
                calculator.compute(
                        defaultPolicy(), saturday, saturday, List.of(), Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("0");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.WEEKLY_OFF);
    }

    @Test
    void holidayIsZeroLopEvenWithNoClockActivity() {
        LocalDate day = LocalDate.of(2026, 1, 5);

        Result result =
                calculator.compute(defaultPolicy(), day, day, List.of(), Set.of(day), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("0");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.HOLIDAY);
    }

    @Test
    void approvedLeaveDayIsExcludedFromAttendanceLop() {
        LocalDate day = LocalDate.of(2026, 1, 5);

        Result result =
                calculator.compute(defaultPolicy(), day, day, List.of(), Set.of(), Set.of(day));

        assertThat(result.lopDays()).isEqualByComparingTo("0");
        assertThat(result.days().get(0).status()).isEqualTo(DailyAttendanceStatus.ON_LEAVE);
    }

    @Test
    void sandwichPolicyConvertsFlankedWeekendToLopWhenEnabled() {
        // Friday absent, Sat/Sun off, Monday absent -> with sandwich policy the weekend also LOPs.
        LocalDate friday = LocalDate.of(2026, 1, 9);
        LocalDate monday = LocalDate.of(2026, 1, 12);
        AttendancePolicy policy = defaultPolicy();
        policy.setSandwichLeavePolicyEnabled(true);

        Result result = calculator.compute(policy, friday, monday, List.of(), Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("4");
        assertThat(statusOn(result, LocalDate.of(2026, 1, 10)))
                .isEqualTo(DailyAttendanceStatus.SANDWICHED_LOP);
        assertThat(statusOn(result, LocalDate.of(2026, 1, 11)))
                .isEqualTo(DailyAttendanceStatus.SANDWICHED_LOP);
    }

    @Test
    void sandwichPolicyDisabledLeavesWeekendUntouched() {
        LocalDate friday = LocalDate.of(2026, 1, 9);
        LocalDate monday = LocalDate.of(2026, 1, 12);

        Result result =
                calculator.compute(defaultPolicy(), friday, monday, List.of(), Set.of(), Set.of());

        assertThat(result.lopDays()).isEqualByComparingTo("2");
        assertThat(statusOn(result, LocalDate.of(2026, 1, 10)))
                .isEqualTo(DailyAttendanceStatus.WEEKLY_OFF);
    }

    @Test
    void sandwichPolicyRequiresBothSidesToBeLopDays() {
        // Friday present, Sat/Sun off, Monday absent -> not flanked on both sides, no sandwich.
        LocalDate friday = LocalDate.of(2026, 1, 9);
        LocalDate monday = LocalDate.of(2026, 1, 12);
        AttendancePolicy policy = defaultPolicy();
        policy.setSandwichLeavePolicyEnabled(true);
        List<TimeEntry> entries =
                List.of(
                        entry(friday, 9, 0, TimeEventType.IN),
                        entry(friday, 17, 0, TimeEventType.OUT));

        Result result = calculator.compute(policy, friday, monday, entries, Set.of(), Set.of());

        assertThat(statusOn(result, LocalDate.of(2026, 1, 10)))
                .isEqualTo(DailyAttendanceStatus.WEEKLY_OFF);
        assertThat(result.lopDays()).isEqualByComparingTo("1");
    }

    private static DailyAttendanceStatus statusOn(Result result, LocalDate date) {
        return result.days().stream()
                .filter(d -> d.date().equals(date))
                .findFirst()
                .map(DayRecord::status)
                .orElseThrow();
    }

    private static AttendancePolicy defaultPolicy() {
        AttendancePolicy p = new AttendancePolicy();
        p.setStandardHoursPerDay(new BigDecimal("8.00"));
        p.setStandardHoursPerWeek(new BigDecimal("40.00"));
        p.setWorkingDays("MON,TUE,WED,THU,FRI");
        return p;
    }

    private static TimeEntry entry(LocalDate day, int hour, int minute, TimeEventType type) {
        TimeEntry e = new TimeEntry();
        e.setEventType(type);
        Instant t =
                LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))
                        .toInstant(ZoneOffset.UTC);
        e.setOccurredAt(t);
        return e;
    }
}
