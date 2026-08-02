package com.ewos.ats.application;

import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.recruitment.domain.JobRequisition;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link AtsEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code AtsEvent}s themselves already existed and
 * were already published by the candidate/application services — nothing was listening to them
 * before this class.
 *
 * <p>Only the two events where the candidate — not the acting recruiter — is the one who changed
 * the state are wired here: an accepted or declined offer, notified to the requisition's hiring
 * manager via {@link JobApplication#getJobRequisition()}. Every other {@code AtsEventType} (all
 * {@code CANDIDATE_*}/{@code RESUME_*}/{@code DOCUMENT_*}/{@code NOTE_*}/{@code
 * COMMUNICATION_LOGGED}, plus {@code APPLICATION_CREATED/STATUS_CHANGED/REJECTED/WITHDRAWN/
 * ON_HOLD/RESUMED/HIRED}) is authored by the recruiter or hiring manager themself — notifying the
 * actor of their own action isn't notification-worthy, the same principle {@code
 * GoalNotificationEventListener} applies to self-authored goal events.
 */
@Component
public class AtsNotificationEventListener {

    private final NotificationService notifications;
    private final JobApplicationRepository applications;

    public AtsNotificationEventListener(
            NotificationService notifications, JobApplicationRepository applications) {
        this.notifications = notifications;
        this.applications = applications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAtsEvent(AtsEvent event) {
        switch (event.eventType()) {
            case APPLICATION_OFFER_ACCEPTED ->
                    notifyHiringManager(
                            event,
                            NotificationType.APPLICATION_OFFER_ACCEPTED,
                            "Candidate accepted the offer",
                            "The candidate for application {{applicationNumber}} accepted their"
                                    + " offer");
            case APPLICATION_OFFER_DECLINED ->
                    notifyHiringManager(
                            event,
                            NotificationType.APPLICATION_OFFER_DECLINED,
                            "Candidate declined the offer",
                            "The candidate for application {{applicationNumber}} declined their"
                                    + " offer");
            default -> {
                // See class javadoc — every other event type is actor-authored bookkeeping with
                // no separate notification-worthy recipient.
            }
        }
    }

    private void notifyHiringManager(
            AtsEvent event, NotificationType type, String title, String body) {
        if (event.applicationId() == null) {
            return;
        }
        applications
                .findByIdAndTenantId(event.applicationId(), event.tenantId())
                .map(JobApplication::getJobRequisition)
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
                                        Map.of(
                                                "applicationNumber",
                                                event.applicationNumber() == null
                                                        ? "your requisition"
                                                        : event.applicationNumber())));
    }
}
