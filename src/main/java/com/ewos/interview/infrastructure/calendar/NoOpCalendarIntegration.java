package com.ewos.interview.infrastructure.calendar;

import com.ewos.interview.domain.CalendarIntegration;
import com.ewos.interview.domain.InterviewRound;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Default {@link CalendarIntegration} binding — no external calls, returns {@code null} for the
 * reference. Deployments that want real calendar reservations override this with a {@code @Primary}
 * bean.
 */
@Component
public class NoOpCalendarIntegration implements CalendarIntegration {

    private static final String PROVIDER = "noop-calendar";

    @Override
    public String scheduleRound(InterviewRound round, List<UUID> participantEmployeeIds) {
        return null;
    }

    @Override
    public String rescheduleRound(InterviewRound round, List<UUID> participantEmployeeIds) {
        return null;
    }

    @Override
    public void cancelRound(InterviewRound round) {
        // no-op
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
