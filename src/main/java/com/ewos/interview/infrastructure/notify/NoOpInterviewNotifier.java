package com.ewos.interview.infrastructure.notify;

import com.ewos.interview.domain.InterviewNotifier;
import com.ewos.interview.domain.InterviewRound;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Default {@link InterviewNotifier} binding — no external calls. */
@Component
public class NoOpInterviewNotifier implements InterviewNotifier {

    private static final String PROVIDER = "noop-notifier";

    @Override
    public void notifyScheduled(InterviewRound round, List<UUID> panelEmployeeIds) {
        // no-op
    }

    @Override
    public void notifyRescheduled(InterviewRound round, List<UUID> panelEmployeeIds) {
        // no-op
    }

    @Override
    public void notifyCancelled(InterviewRound round, List<UUID> panelEmployeeIds) {
        // no-op
    }

    @Override
    public void notifyReminder(InterviewRound round, List<UUID> panelEmployeeIds) {
        // no-op
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
