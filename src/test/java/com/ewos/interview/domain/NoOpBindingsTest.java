package com.ewos.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.interview.infrastructure.calendar.NoOpCalendarIntegration;
import com.ewos.interview.infrastructure.notify.NoOpInterviewNotifier;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpBindingsTest {

    @Test
    void calendarIntegrationReturnsNullRef() {
        NoOpCalendarIntegration cal = new NoOpCalendarIntegration();
        assertThat(cal.scheduleRound(new InterviewRound(), List.of(UUID.randomUUID()))).isNull();
        assertThat(cal.rescheduleRound(new InterviewRound(), List.of())).isNull();
        assertThat(cal.providerId()).isEqualTo("noop-calendar");
    }

    @Test
    void notifierNoOpsAndReports() {
        NoOpInterviewNotifier not = new NoOpInterviewNotifier();
        not.notifyScheduled(new InterviewRound(), List.of(UUID.randomUUID()));
        not.notifyRescheduled(new InterviewRound(), List.of());
        not.notifyCancelled(new InterviewRound(), List.of());
        not.notifyReminder(new InterviewRound(), List.of());
        assertThat(not.providerId()).isEqualTo("noop-notifier");
    }
}
