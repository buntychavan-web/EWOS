package com.ewos.interview.application;

import com.ewos.employee.domain.Employee;
import com.ewos.interview.domain.InterviewParticipant;
import com.ewos.interview.domain.InterviewRound;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.infrastructure.persistence.InterviewParticipantRepository;
import com.ewos.interview.infrastructure.persistence.InterviewRoundRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.recruitment.domain.JobRequisition;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link InterviewEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code InterviewEvent}s themselves already existed
 * and were already published by {@link InterviewRoundService}/{@link InterviewPanelService} —
 * nothing was listening to them before this class. This is the in-app half of what {@code
 * NoOpInterviewNotifier} deliberately leaves as a no-op default: that seam is for
 * candidate/external-panel-vendor notifications (email/SMS to people with no login), which this
 * in-app inbox cannot deliver to; this listener covers the internal side — the panel members and
 * hiring manager, both of whom are {@code Employee}s with a platform login.
 *
 * <p>{@code TEMPLATE_*} events are catalog housekeeping. {@code ROUND_CREATED/STARTED/NO_SHOW},
 * {@code PANEL_ADDED/REMOVED/ATTENDANCE_UPDATED}, and {@code CANDIDATE_FEEDBACK_SUBMITTED} are
 * either actor-authored bookkeeping or — for panel changes — the event carries no structured
 * per-participant id to notify a single added/removed panelist unambiguously.
 */
@Component
public class InterviewNotificationEventListener {

    private final NotificationService notifications;
    private final InterviewRoundRepository rounds;
    private final InterviewParticipantRepository participants;

    public InterviewNotificationEventListener(
            NotificationService notifications,
            InterviewRoundRepository rounds,
            InterviewParticipantRepository participants) {
        this.notifications = notifications;
        this.rounds = rounds;
        this.participants = participants;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInterviewEvent(InterviewEvent event) {
        switch (event.eventType()) {
            case ROUND_SCHEDULED ->
                    notifyPanel(
                            event,
                            NotificationType.INTERVIEW_ROUND_SCHEDULED,
                            "Interview scheduled",
                            "You've been scheduled on an interview panel");
            case ROUND_RESCHEDULED ->
                    notifyPanel(
                            event,
                            NotificationType.INTERVIEW_ROUND_RESCHEDULED,
                            "Interview rescheduled",
                            "An interview you're on the panel for has been rescheduled");
            case ROUND_CANCELLED ->
                    notifyPanel(
                            event,
                            NotificationType.INTERVIEW_ROUND_CANCELLED,
                            "Interview cancelled",
                            "An interview you're on the panel for has been cancelled");
            case SCORECARD_SUBMITTED ->
                    notifyHiringManager(
                            event,
                            NotificationType.INTERVIEW_SCORECARD_SUBMITTED,
                            "Interview scorecard submitted",
                            "A panel member submitted their interview scorecard");
            case ROUND_DECIDED ->
                    notifyHiringManager(
                            event,
                            NotificationType.INTERVIEW_ROUND_DECIDED,
                            "Interview round decided",
                            "An interview round outcome has been recorded");
            default -> {
                // See class javadoc for why the remaining event types are not notification-worthy.
            }
        }
    }

    private void notifyPanel(
            InterviewEvent event, NotificationType type, String title, String body) {
        if (event.roundId() == null) {
            return;
        }
        List<InterviewParticipant> panel =
                participants.findAllByTenantIdAndRoundIdOrderByCreatedAtAsc(
                        event.tenantId(), event.roundId());
        for (InterviewParticipant participant : panel) {
            UUID userId =
                    participant.getEmployee() == null
                            ? null
                            : participant.getEmployee().getUserId();
            if (userId != null) {
                notifications.send(event.tenantId(), userId, type, title, body, null);
            }
        }
    }

    private void notifyHiringManager(
            InterviewEvent event, NotificationType type, String title, String body) {
        if (event.roundId() == null) {
            return;
        }
        rounds.findByIdAndTenantId(event.roundId(), event.tenantId())
                .map(InterviewRound::getApplication)
                .map(a -> a.getJobRequisition())
                .map(JobRequisition::getHiringManager)
                .map(Employee::getUserId)
                .ifPresent(
                        userId ->
                                notifications.send(
                                        event.tenantId(),
                                        userId,
                                        type,
                                        title,
                                        body,
                                        null,
                                        Map.of()));
    }
}
