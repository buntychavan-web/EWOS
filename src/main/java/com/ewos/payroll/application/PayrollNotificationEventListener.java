package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.payroll.domain.events.PayrollEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link PayrollEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code PayrollEvent}s themselves already existed
 * and were already published by {@link PayrollRunService}/{@link PayslipService} — nothing was
 * listening to them before this class.
 *
 * <p>{@code PAYSLIP_FINALIZED} notifies the employee it belongs to directly (the event carries
 * {@code employeeId}). {@code RUN_FINALIZED}/{@code RUN_FAILED} notify {@code actorId} — whoever
 * ran the payroll run — the same "notify the requester their async job finished" pattern {@code
 * PerformanceNotificationEventListener} uses for {@code CYCLE_LAUNCH_COMPLETED}.
 *
 * <p><b>"Payroll run awaiting approval" notifications</b> are handled separately by {@link
 * PayrollApprovalNotificationListener} (Sprint 24L), not here — that class resolves the current
 * approval level's role to concrete users via {@code ApproverResolver} and notifies each one
 * directly, closing the role-holder-fan-out gap this class used to document as an open follow-up.
 *
 * <p>{@code COMPONENT_CHANGED}, {@code PERIOD_OPENED/LOCKED/CLOSED}, {@code COMPENSATION_CHANGED},
 * {@code RUN_STARTED}, {@code RUN_COMPLETED} (the workflow-start trigger, not a human-facing
 * outcome), {@code RUN_FROZEN}, {@code PAYSLIP_GENERATED} (draft, not final), and {@code
 * ARREAR_QUEUED/APPLIED} are HR/finance-admin bookkeeping authored by the actor themself.
 */
@Component
public class PayrollNotificationEventListener {

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    public PayrollNotificationEventListener(
            NotificationService notifications, EmployeeRepository employees) {
        this.notifications = notifications;
        this.employees = employees;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPayrollEvent(PayrollEvent event) {
        switch (event.eventType()) {
            case PAYSLIP_FINALIZED -> notifyEmployee(event);
            case RUN_FINALIZED ->
                    notifyActor(
                            event,
                            NotificationType.PAYROLL_RUN_FINALIZED,
                            "Payroll run finalized",
                            "The payroll run you finalized has completed");
            case RUN_FAILED ->
                    notifyActor(
                            event,
                            NotificationType.PAYROLL_RUN_FAILED,
                            "Payroll run failed",
                            "The payroll run you started has failed and needs attention");
            default -> {
                // See class javadoc for why the remaining event types are not notification-worthy.
            }
        }
    }

    private void notifyEmployee(PayrollEvent event) {
        if (event.employeeId() == null) {
            return;
        }
        employees
                .findByIdAndTenantId(event.employeeId(), event.tenantId())
                .map(Employee::getUserId)
                .ifPresent(
                        userId ->
                                notifications.send(
                                        event.tenantId(),
                                        userId,
                                        NotificationType.PAYSLIP_READY,
                                        "Payslip ready",
                                        "Your payslip is ready to view",
                                        null));
    }

    private void notifyActor(PayrollEvent event, NotificationType type, String title, String body) {
        if (event.actorId() == null) {
            return;
        }
        notifications.send(event.tenantId(), event.actorId(), type, title, body, null);
    }
}
