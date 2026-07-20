package com.ewos.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.attendance.domain.TimesheetCalculator.Totals;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimesheetCalculatorTest {

    private final TimesheetCalculator calc = new TimesheetCalculator();

    @Test
    void noEntriesYieldsZeroTotals() {
        AttendancePolicy policy = defaultPolicy();
        Totals totals =
                calc.calculate(
                        policy, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 9), List.of());

        assertThat(totals.workedHours()).isEqualByComparingTo("0.00");
        assertThat(totals.overtimeHours()).isEqualByComparingTo("0.00");
        assertThat(totals.absenceHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void oneEightHourShiftWithinStandardYieldsNoOvertime() {
        AttendancePolicy policy = defaultPolicy();
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries =
                List.of(entry(day, 9, 0, TimeEventType.IN), entry(day, 17, 0, TimeEventType.OUT));

        Totals totals = calc.calculate(policy, day, day, entries);
        assertThat(totals.workedHours()).isEqualByComparingTo("8.00");
        assertThat(totals.overtimeHours()).isEqualByComparingTo("0.00");
        assertThat(totals.absenceHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void breakDeductsFromWorked() {
        AttendancePolicy policy = defaultPolicy();
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries =
                List.of(
                        entry(day, 9, 0, TimeEventType.IN),
                        entry(day, 12, 0, TimeEventType.BREAK_START),
                        entry(day, 13, 0, TimeEventType.BREAK_END),
                        entry(day, 18, 0, TimeEventType.OUT));

        Totals totals = calc.calculate(policy, day, day, entries);
        assertThat(totals.workedHours()).isEqualByComparingTo("8.00");
        assertThat(totals.breakHours()).isEqualByComparingTo("1.00");
    }

    @Test
    void extraHoursCountAsOvertime() {
        AttendancePolicy policy = defaultPolicy();
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries =
                List.of(entry(day, 8, 0, TimeEventType.IN), entry(day, 20, 0, TimeEventType.OUT));

        Totals totals = calc.calculate(policy, day, day, entries);
        assertThat(totals.workedHours()).isEqualByComparingTo("12.00");
        assertThat(totals.overtimeHours()).isEqualByComparingTo("4.00");
    }

    @Test
    void shortShiftYieldsAbsence() {
        AttendancePolicy policy = defaultPolicy();
        LocalDate day = LocalDate.of(2026, 1, 5);
        List<TimeEntry> entries =
                List.of(entry(day, 9, 0, TimeEventType.IN), entry(day, 14, 0, TimeEventType.OUT));

        Totals totals = calc.calculate(policy, day, day, entries);
        assertThat(totals.workedHours()).isEqualByComparingTo("5.00");
        assertThat(totals.absenceHours()).isEqualByComparingTo("3.00");
    }

    @Test
    void unpairedInIsIgnored() {
        AttendancePolicy policy = defaultPolicy();
        LocalDate day = LocalDate.of(2026, 1, 5);
        // Only an IN — the shift is still open, no worked hours should count.
        List<TimeEntry> entries = List.of(entry(day, 9, 0, TimeEventType.IN));

        Totals totals = calc.calculate(policy, day, day, entries);
        assertThat(totals.workedHours()).isEqualByComparingTo("0.00");
    }

    @Test
    void weekendsSkippedInWorkingDayCount() {
        AttendancePolicy policy = defaultPolicy();
        // Period Monday-Sunday should count as 5 working days.
        LocalDate mon = LocalDate.of(2026, 1, 5);
        LocalDate sun = LocalDate.of(2026, 1, 11);
        List<TimeEntry> entries =
                List.of(entry(mon, 9, 0, TimeEventType.IN), entry(mon, 17, 0, TimeEventType.OUT));

        Totals totals = calc.calculate(policy, mon, sun, entries);
        // Standard expected = 5 days * 8 hours = 40. Worked = 8. Absence = 32.
        assertThat(totals.absenceHours()).isEqualByComparingTo("32.00");
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
