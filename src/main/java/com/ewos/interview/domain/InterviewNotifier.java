package com.ewos.interview.domain;

import java.util.List;
import java.util.UUID;

/**
 * Contract for candidate / interviewer notifications (email, SMS, ...). The default in-tree binding
 * no-ops so the flow works end-to-end without a mail server; deployments plug in real senders as a
 * {@code @Primary} bean.
 */
public interface InterviewNotifier {

    void notifyScheduled(InterviewRound round, List<UUID> panelEmployeeIds);

    void notifyRescheduled(InterviewRound round, List<UUID> panelEmployeeIds);

    void notifyCancelled(InterviewRound round, List<UUID> panelEmployeeIds);

    void notifyReminder(InterviewRound round, List<UUID> panelEmployeeIds);

    String providerId();
}
