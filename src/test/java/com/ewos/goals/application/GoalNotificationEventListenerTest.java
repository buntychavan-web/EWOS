package com.ewos.goals.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.domain.events.GoalEventType;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalNotificationEventListenerTest {

    @Mock NotificationService notifications;
    @Mock EmployeeRepository employees;
    @Mock GoalRepository goals;

    private GoalNotificationEventListener listener;
    private UUID tenantId;
    private UUID goalId;
    private UUID employeeId;
    private UUID employeeUserId;

    @BeforeEach
    void setUp() {
        listener = new GoalNotificationEventListener(notifications, employees, goals);
        tenantId = UUID.randomUUID();
        goalId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        employeeUserId = UUID.randomUUID();
    }

    private GoalEvent event(GoalEventType type) {
        return new GoalEvent(
                type,
                tenantId,
                UUID.randomUUID(),
                goalId,
                null,
                employeeId,
                null,
                null,
                UUID.randomUUID(),
                Instant.now());
    }

    private static Employee employeeWithUser(UUID userId) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        return e;
    }

    @Test
    void goalAssignedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onGoalEvent(event(GoalEventType.GOAL_ASSIGNED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.GOAL_ASSIGNED),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void goalCompletedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onGoalEvent(event(GoalEventType.GOAL_COMPLETED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.GOAL_COMPLETED),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void goalUnderReviewNotifiesTheManagerNotTheEmployee() {
        Employee manager = employeeWithUser(UUID.randomUUID());
        Employee employee = employeeWithUser(employeeUserId);
        employee.setManager(manager);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        listener.onGoalEvent(event(GoalEventType.GOAL_UNDER_REVIEW));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(manager.getUserId()),
                        eq(NotificationType.GOAL_REVIEW_PENDING),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void goalUnderReviewSkipsWhenEmployeeHasNoManager() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onGoalEvent(event(GoalEventType.GOAL_UNDER_REVIEW));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void skipsWhenEmployeeHasNoLinkedUser() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        listener.onGoalEvent(event(GoalEventType.GOAL_ASSIGNED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void unmappedEventTypesDoNothing() {
        listener.onGoalEvent(event(GoalEventType.GOAL_CREATED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any(), anyMap());
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }
}
