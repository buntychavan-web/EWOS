package com.ewos.employee.application;

import com.ewos.attendance.domain.events.AttendanceEvent;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 27B — bridges Leave/Timesheet submit/approve/reject to in-app notifications, mirroring
 * {@code PerformanceNotificationEventListener}'s pattern exactly (recipient resolved as a user id,
 * {@code AFTER_COMMIT} timing — {@link NotificationService#send} requires it, see that method's
 * Javadoc). Neither module produced any notification at all before this sprint: a manager was never
 * told a request was waiting, and an employee was never told the outcome.
 */
@Component
public class EssMssNotificationEventListener {

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    public EssMssNotificationEventListener(
            NotificationService notifications, EmployeeRepository employees) {
        this.notifications = notifications;
        this.employees = employees;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaveEvent(LeaveEvent event) {
        switch (event.eventType()) {
            case REQUEST_SUBMITTED ->
                    notifyManager(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_PENDING,
                            "Leave request awaiting your approval",
                            "A direct report submitted a leave request that needs your decision.");
            case REQUEST_APPROVED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_APPROVED,
                            "Leave request approved",
                            "Your leave request has been approved.");
            case REQUEST_REJECTED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_REJECTED,
                            "Leave request rejected",
                            "Your leave request has been rejected.");
            default -> {
                // REQUEST_CREATED/CANCELLED/ALLOCATION_CHANGED/BALANCE_ADJUSTED — not part of the
                // approvals-inbox flow this listener exists for.
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAttendanceEvent(AttendanceEvent event) {
        switch (event.eventType()) {
            case TIMESHEET_SUBMITTED ->
                    notifyManager(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_PENDING,
                            "Timesheet awaiting your approval",
                            "A direct report submitted a timesheet that needs your decision.");
            case TIMESHEET_APPROVED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_APPROVED,
                            "Timesheet approved",
                            "Your timesheet has been approved.");
            case TIMESHEET_REJECTED ->
                    notifyEmployee(
                            event.tenantId(),
                            event.employeeId(),
                            NotificationType.ESS_MSS_APPROVAL_REJECTED,
                            "Timesheet rejected",
                            "Your timesheet has been rejected.");
            default -> {
                // TIME_ENTRY_*/TIMESHEET_CREATED/CANCELLED — not part of the approvals-inbox flow.
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
