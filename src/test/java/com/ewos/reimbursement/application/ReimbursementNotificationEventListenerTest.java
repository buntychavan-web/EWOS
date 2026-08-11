package com.ewos.reimbursement.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.reimbursement.domain.events.ReimbursementEvent;
import com.ewos.reimbursement.domain.events.ReimbursementEventType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mirrors {@code EssMssNotificationEventListener}'s own coverage shape: one test per event type.
 */
@ExtendWith(MockitoExtension.class)
class ReimbursementNotificationEventListenerTest {

    @Mock NotificationService notifications;
    @Mock EmployeeRepository employees;

    private ReimbursementNotificationEventListener listener;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID employeeUserId = UUID.randomUUID();
    private final UUID managerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new ReimbursementNotificationEventListener(notifications, employees);
    }

    private Employee employeeWithManager() {
        Employee manager = new Employee();
        manager.setUserId(managerUserId);
        Employee employee = new Employee();
        employee.setUserId(employeeUserId);
        employee.setManager(manager);
        return employee;
    }

    private ReimbursementEvent event(ReimbursementEventType type) {
        return new ReimbursementEvent(
                type,
                tenantId,
                UUID.randomUUID(),
                employeeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now());
    }

    @Test
    void submittedNotifiesTheEmployeesManager() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithManager()));

        listener.onReimbursementEvent(event(ReimbursementEventType.SUBMITTED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(managerUserId),
                        eq(NotificationType.REIMBURSEMENT_SUBMITTED),
                        any(),
                        any(),
                        any());
    }

    @Test
    void submittedDoesNothingWhenTheEmployeeHasNoManager() {
        Employee employee = new Employee();
        employee.setUserId(employeeUserId);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        listener.onReimbursementEvent(event(ReimbursementEventType.SUBMITTED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void managerApprovedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithManager()));

        listener.onReimbursementEvent(event(ReimbursementEventType.MANAGER_APPROVED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.REIMBURSEMENT_MANAGER_APPROVED),
                        any(),
                        any(),
                        any());
    }

    @Test
    void managerRejectedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithManager()));

        listener.onReimbursementEvent(event(ReimbursementEventType.MANAGER_REJECTED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.REIMBURSEMENT_MANAGER_REJECTED),
                        any(),
                        any(),
                        any());
    }

    @Test
    void financeApprovedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithManager()));

        listener.onReimbursementEvent(event(ReimbursementEventType.FINANCE_APPROVED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.REIMBURSEMENT_FINANCE_APPROVED),
                        any(),
                        any(),
                        any());
    }

    @Test
    void financeRejectedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithManager()));

        listener.onReimbursementEvent(event(ReimbursementEventType.FINANCE_REJECTED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.REIMBURSEMENT_FINANCE_REJECTED),
                        any(),
                        any(),
                        any());
    }
}
