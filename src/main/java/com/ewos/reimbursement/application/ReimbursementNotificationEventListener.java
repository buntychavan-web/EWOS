package com.ewos.reimbursement.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.reimbursement.domain.events.ReimbursementEvent;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 2 — bridges reimbursement claim lifecycle events to in-app notifications, mirroring {@code
 * EssMssNotificationEventListener}'s pattern exactly (recipient resolved as a user id, {@code
 * AFTER_COMMIT} timing).
 */
@Component
public class ReimbursementNotificationEventListener {

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    public ReimbursementNotificationEventListener(
            NotificationService notifications, EmployeeRepository employees) {
        this.notifications = notifications;
        this.employees = employees;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReimbursementEvent(ReimbursementEvent event) {
        switch (event.eventType()) {
            case SUBMITTED ->
                    notifyManager(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.REIMBURSEMENT_SUBMITTED,
                            "Reimbursement claim awaiting your approval",
                            "A direct report submitted a reimbursement claim that needs your decision.");
            case MANAGER_APPROVED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.REIMBURSEMENT_MANAGER_APPROVED,
                            "Reimbursement claim approved by your manager",
                            "Your reimbursement claim has been approved by your manager and is now"
                                    + " with finance for review.");
            case MANAGER_REJECTED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.REIMBURSEMENT_MANAGER_REJECTED,
                            "Reimbursement claim rejected",
                            "Your reimbursement claim has been rejected by your manager.");
            case FINANCE_APPROVED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.REIMBURSEMENT_FINANCE_APPROVED,
                            "Reimbursement claim approved",
                            "Your reimbursement claim has been approved by finance.");
            case FINANCE_REJECTED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.REIMBURSEMENT_FINANCE_REJECTED,
                            "Reimbursement claim rejected",
                            "Your reimbursement claim has been rejected by finance.");
            default -> {
                // Exhaustive over ReimbursementEventType today; required by checkstyle.
            }
        }
    }

    private void notifyManager(
            UUID tenantId, UUID employeeId, NotificationType type, String title, String body) {
        Employee employee = employees.findByIdAndTenantId(employeeId, tenantId).orElse(null);
        if (employee == null || employee.getManager() == null) {
            return;
        }
        UUID managerUserId = employee.getManager().getUserId();
        if (managerUserId != null) {
            notifications.send(tenantId, managerUserId, type, title, body, null);
        }
    }

    private void notifyEmployee(
            UUID tenantId, UUID employeeId, NotificationType type, String title, String body) {
        Employee employee = employees.findByIdAndTenantId(employeeId, tenantId).orElse(null);
        if (employee == null || employee.getUserId() == null) {
            return;
        }
        notifications.send(tenantId, employee.getUserId(), type, title, body, null);
    }
}
