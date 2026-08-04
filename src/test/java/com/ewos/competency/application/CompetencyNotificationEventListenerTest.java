package com.ewos.competency.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.competency.infrastructure.persistence.DevelopmentPlanRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
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
class CompetencyNotificationEventListenerTest {

    @Mock NotificationService notifications;
    @Mock EmployeeRepository employees;
    @Mock CompetencyRepository competencies;
    @Mock DevelopmentPlanRepository plans;

    private CompetencyNotificationEventListener listener;
    private UUID tenantId;
    private UUID employeeId;
    private UUID employeeUserId;

    @BeforeEach
    void setUp() {
        listener =
                new CompetencyNotificationEventListener(
                        notifications, employees, competencies, plans);
        tenantId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        employeeUserId = UUID.randomUUID();
    }

    private CompetencyEvent event(CompetencyEventType type) {
        return new CompetencyEvent(
                type,
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "MANAGER",
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
    void assessmentRecordedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onCompetencyEvent(event(CompetencyEventType.ASSESSMENT_RECORDED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.COMPETENCY_ASSESSED),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void planActivatedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onCompetencyEvent(event(CompetencyEventType.PLAN_ACTIVATED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.DEVPLAN_ACTIVATED),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void planCompletedNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onCompetencyEvent(event(CompetencyEventType.PLAN_COMPLETED));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.DEVPLAN_COMPLETED),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void actionDueReminderNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onCompetencyEvent(event(CompetencyEventType.ACTION_DUE_REMINDER));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.DEVPLAN_ACTION_DUE),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void actionOverdueNotifiesTheEmployee() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeWithUser(employeeUserId)));

        listener.onCompetencyEvent(event(CompetencyEventType.ACTION_OVERDUE));

        verify(notifications)
                .send(
                        eq(tenantId),
                        eq(employeeUserId),
                        eq(NotificationType.DEVPLAN_ACTION_OVERDUE),
                        any(),
                        any(),
                        isNull(),
                        anyMap());
    }

    @Test
    void skipsWhenEmployeeHasNoLinkedUser() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        listener.onCompetencyEvent(event(CompetencyEventType.ASSESSMENT_RECORDED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void unmappedEventTypesDoNothing() {
        listener.onCompetencyEvent(event(CompetencyEventType.COMPETENCY_CREATED));

        verify(notifications, never()).send(any(), any(), any(), any(), any(), any(), anyMap());
        verify(employees, never()).findByIdAndTenantId(any(), any());
    }
}
