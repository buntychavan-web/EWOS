package com.ewos.payroll.application;

import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.payroll.domain.events.PayrollApprovalEvent;
import com.ewos.workflow.application.ApproverResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link PayrollApprovalEvent} to in-app notifications, at {@code AFTER_COMMIT} — {@link
 * NotificationService#send} is documented to require this timing ({@code REQUIRES_NEW} only opens a
 * genuinely fresh transaction when the triggering transaction has already committed; see that
 * method's Javadoc for the exact silent-data-loss bug this avoids). Approvers are re-resolved at
 * delivery time via {@link ApproverResolver} rather than snapshotted on the event, so a
 * role-membership change between submission and delivery is reflected correctly.
 *
 * <p>Fixes a real gap the Codex audit's research surfaced in the workflow engine this replaces:
 * {@code WorkflowNotificationListener} only notifies {@code USER}/{@code EMPLOYEE}-assigned tasks,
 * never bare {@code ROLE} assignments — so the old {@code PayrollApprovalWorkflowListener} (which
 * assigned by {@code ROLE}) never actually notified anyone. This listener resolves the role to
 * concrete users itself before sending.
 */
@Component
public class PayrollApprovalNotificationListener {

    private final NotificationService notifications;
    private final ApproverResolver approverResolver;

    public PayrollApprovalNotificationListener(
            NotificationService notifications, ApproverResolver approverResolver) {
        this.notifications = notifications;
        this.approverResolver = approverResolver;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayrollApprovalEvent(PayrollApprovalEvent event) {
        switch (event.eventType()) {
            case SUBMITTED, LEVEL_ADVANCED -> notifyApprovers(event);
            case FULLY_APPROVED ->
                    notifications.send(
                            event.tenantId(),
                            event.preparerId(),
                            NotificationType.PAYROLL_APPROVAL_FULLY_APPROVED,
                            "Payroll run approved",
                            "Your payroll run has cleared every approval level and has been"
                                    + " finalized.",
                            null);
            case REJECTED ->
                    notifications.send(
                            event.tenantId(),
                            event.preparerId(),
                            NotificationType.PAYROLL_APPROVAL_REJECTED,
                            "Payroll run rejected",
                            "Your payroll run was rejected at approval level "
                                    + event.levelNumber()
                                    + (event.comments() != null && !event.comments().isBlank()
                                            ? ": " + event.comments()
                                            : "."),
                            null);
            default -> {
                // Exhaustive over PayrollApprovalEventType; no other event type is published today.
            }
        }
    }

    private void notifyApprovers(PayrollApprovalEvent event) {
        approverResolver
                .resolve(event.tenantId(), event.companyId(), null, event.approverRoleCode())
                .forEach(
                        approver ->
                                notifications.send(
                                        event.tenantId(),
                                        approver.actorId(),
                                        NotificationType.PAYROLL_APPROVAL_PENDING,
                                        "Payroll run awaiting your approval",
                                        "A payroll run is waiting for your decision at approval"
                                                + " level "
                                                + event.levelNumber()
                                                + ".",
                                        null));
    }
}
