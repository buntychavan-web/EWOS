package com.ewos.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HolidayTest {

    @Test
    void fixedDateHolidayOnlyMatchesExactYear() {
        Holiday h = new Holiday();
        h.setHolidayDate(LocalDate.of(2026, 1, 26));
        h.setRecurringAnnually(false);

        assertThat(h.fallsOn(LocalDate.of(2026, 1, 26))).isTrue();
        assertThat(h.fallsOn(LocalDate.of(2027, 1, 26))).isFalse();
    }

    @Test
    void recurringHolidayMatchesEveryYearOnTheSameMonthAndDay() {
        Holiday h = new Holiday();
        h.setHolidayDate(LocalDate.of(2020, 1, 26));
        h.setRecurringAnnually(true);

        assertThat(h.fallsOn(LocalDate.of(2026, 1, 26))).isTrue();
        assertThat(h.fallsOn(LocalDate.of(2031, 1, 26))).isTrue();
        assertThat(h.fallsOn(LocalDate.of(2026, 1, 27))).isFalse();
    }
}
