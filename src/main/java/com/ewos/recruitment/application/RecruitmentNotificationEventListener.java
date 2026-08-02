package com.ewos.recruitment.application;

import com.ewos.employee.domain.Employee;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.events.RecruitmentEvent;
import com.ewos.recruitment.infrastructure.persistence.JobRequisitionRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link RecruitmentEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code RecruitmentEvent}s themselves already
 * existed and were already published by {@link JobRequisitionService} — nothing was listening to
 * them before this class (see the Sprint 24F CTO readiness review, "Recruitment — notifications").
 *
 * <p>Only requisition-outcome events with one unambiguous individual recipient — the requisition's
 * {@code hiringManager} — are wired here. {@code POSITION_*} events are catalog/HR-admin
 * housekeeping with no single recipient. {@code REQUISITION_CREATED/UPDATED/SUBMITTED/OPENED/
 * HELD/RESUMED/CANCELLED} are authored by the hiring manager themself (or an HR admin still
 * drafting); submission itself already notifies the approver via the Workflow Engine's own {@code
 * TASK_ASSIGNED} notification (see {@code WorkflowNotificationListener}) — duplicating that here
 * would be a second notification for the same action.
 */
@Component
public class RecruitmentNotificationEventListener {

    private final NotificationService notifications;
    private final JobRequisitionRepository requisitions;

    public RecruitmentNotificationEventListener(
            NotificationService notifications, JobRequisitionRepository requisitions) {
        this.notifications = notifications;
        this.requisitions = requisitions;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecruitmentEvent(RecruitmentEvent event) {
        switch (event.eventType()) {
            case REQUISITION_APPROVED ->
                    notifyHiringManager(
                            event,
                            NotificationType.REQUISITION_APPROVED,
                            "Requisition approved",
                            "Your requisition {{requisitionNumber}} has been approved");
            case REQUISITION_REJECTED ->
                    notifyHiringManager(
                            event,
                            NotificationType.REQUISITION_REJECTED,
                            "Requisition rejected",
                            "Your requisition {{requisitionNumber}} was rejected");
            case REQUISITION_FILLED ->
                    notifyHiringManager(
                            event,
                            NotificationType.REQUISITION_FILLED,
                            "Requisition filled",
                            "Your requisition {{requisitionNumber}} has been filled");
            case REQUISITION_CLOSED ->
                    notifyHiringManager(
                            event,
                            NotificationType.REQUISITION_CLOSED,
                            "Requisition closed",
                            "Your requisition {{requisitionNumber}} was closed");
            default -> {
                // POSITION_*, REQUISITION_CREATED/UPDATED/SUBMITTED/OPENED/HELD/RESUMED/CANCELLED:
                // no notification-worthy individual recipient — see class javadoc.
            }
        }
    }

    private void notifyHiringManager(
            RecruitmentEvent event, NotificationType type, String title, String body) {
        if (event.jobRequisitionId() == null) {
            return;
        }
        requisitions
                .findByIdAndTenantId(event.jobRequisitionId(), event.tenantId())
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
                                                "requisitionNumber",
                                                event.requisitionNumber() == null
                                                        ? "your requisition"
                                                        : event.requisitionNumber())));
    }
}
