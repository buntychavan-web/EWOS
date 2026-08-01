package com.ewos.performance.application;

import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.AppraisalParticipantUserIds;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24B — bridges {@link PerformanceEvent}s to the in-app notification inbox, mirroring {@code
 * WorkflowNotificationListener}'s shape exactly: one {@code IN_APP} row written today per
 * notification-worthy event, through the same {@link NotificationService#send} call site every
 * other channel (email, SMS, push) would eventually share — adding a channel means a second
 * implementation of that call site, not a change to this listener or to any publisher.
 *
 * <p>Deliberately maps only per-appraisal-scoped events, each bounded to exactly one human action
 * (a self-assessment submitted, a manager assessment submitted, an appraisal finalised) or one
 * scheduled reminder check — never a whole-cycle event. {@code CYCLE_LAUNCH_COMPLETED} is the one
 * exception, and it notifies only the actor who requested the launch, not every affected employee:
 * firing one event per newly-created appraisal here would mean up to 100k+ notification rows
 * written synchronously off a single bulk-launch job, which is exactly the kind of unbounded
 * fan-out Sprint 24B's performance requirements rule out. A bulk "cycle opened" digest to affected
 * employees, if wanted, belongs in a separate rate-limited/batched job, not this listener.
 */
@Component
public class PerformanceNotificationEventListener {

    private final NotificationService notifications;
    private final AppraisalRepository appraisals;

    public PerformanceNotificationEventListener(
            NotificationService notifications, AppraisalRepository appraisals) {
        this.notifications = notifications;
        this.appraisals = appraisals;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPerformanceEvent(PerformanceEvent event) {
        switch (event.eventType()) {
            case APPRAISAL_OPENED ->
                    notifyEmployee(
                            event,
                            NotificationType.PERF_SELF_REVIEW_OPENED,
                            "Self review opened",
                            "A new appraisal is waiting on your self assessment");
            case SELF_ASSESSMENT_SUBMITTED ->
                    notifyManager(
                            event,
                            NotificationType.PERF_MANAGER_REVIEW_PENDING,
                            "Manager review pending",
                            "Your direct report submitted their self assessment — your review is next");
            case MANAGER_ASSESSMENT_SUBMITTED ->
                    notifyReviewer(
                            event,
                            NotificationType.PERF_REVIEWER_REVIEW_PENDING,
                            "Reviewer review pending",
                            "A manager assessment was submitted and needs your review");
            case APPRAISAL_FINALISED ->
                    notifyEmployee(
                            event,
                            NotificationType.PERF_FINAL_RATING_RELEASED,
                            "Your final rating is available",
                            "Your appraisal has been finalised");
            case SELF_REVIEW_REMINDER ->
                    notifyEmployee(
                            event,
                            NotificationType.PERF_REVIEW_REMINDER,
                            "Reminder: self assessment due",
                            "Your self assessment is still pending");
            case MANAGER_REVIEW_REMINDER ->
                    notifyManager(
                            event,
                            NotificationType.PERF_REVIEW_REMINDER,
                            "Reminder: manager review due",
                            "A manager assessment you own is still pending");
            case REVIEWER_REVIEW_REMINDER ->
                    notifyReviewer(
                            event,
                            NotificationType.PERF_REVIEW_REMINDER,
                            "Reminder: reviewer review due",
                            "A reviewer assessment you own is still pending");
            case CYCLE_LAUNCH_COMPLETED ->
                    notifications.send(
                            event.tenantId(),
                            event.actorId(),
                            NotificationType.PERF_BULK_LAUNCH_COMPLETED,
                            "Bulk appraisal launch completed",
                            event.detail(),
                            null);
            default -> {
                // CYCLE_CREATED/OPENED/ADVANCED/RELEASED/CLOSED/CANCELLED, TEMPLATE_*,
                // REVIEWER_ASSESSMENT_SUBMITTED, CALIBRATION_*, APPRAISAL_SUBMITTED_FOR_APPROVAL/
                // APPROVED/REJECTED/CANCELLED, INCREMENT_/PROMOTION_RECOMMENDED: no single
                // unambiguous individual recipient at this layer (cycle-wide, or an HR/committee
                // concern rather than one employee's).
            }
        }
    }

    private void notifyEmployee(
            PerformanceEvent event, NotificationType type, String title, String body) {
        participants(event)
                .ifPresent(
                        p ->
                                notifications.send(
                                        event.tenantId(),
                                        p.getEmployeeUserId(),
                                        type,
                                        title,
                                        body,
                                        null));
    }

    private void notifyManager(
            PerformanceEvent event, NotificationType type, String title, String body) {
        participants(event)
                .ifPresent(
                        p ->
                                notifications.send(
                                        event.tenantId(),
                                        p.getManagerUserId(),
                                        type,
                                        title,
                                        body,
                                        null));
    }

    private void notifyReviewer(
            PerformanceEvent event, NotificationType type, String title, String body) {
        participants(event)
                .ifPresent(
                        p ->
                                notifications.send(
                                        event.tenantId(),
                                        p.getReviewerUserId(),
                                        type,
                                        title,
                                        body,
                                        null));
    }

    private Optional<AppraisalParticipantUserIds> participants(PerformanceEvent event) {
        if (event.appraisalId() == null) {
            return Optional.empty();
        }
        return appraisals.findParticipantUserIds(event.appraisalId(), event.tenantId());
    }
}
