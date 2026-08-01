package com.ewos.goals.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.domain.events.GoalEventType;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoalReminderJobTest {

    @Mock GoalRepository goals;
    @Mock ApplicationEventPublisher events;

    private GoalReminderJob job;

    @BeforeEach
    void setUp() {
        job = new GoalReminderJob(goals, events);
        ReflectionTestUtils.setField(job, "batchSize", 5000);
        ReflectionTestUtils.setField(job, "dueSoonDays", 3);
    }

    private Goal goalWithEmployee() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        Goal g = new Goal();
        g.setId(UUID.randomUUID());
        g.setTenantId(UUID.randomUUID());
        g.setCompanyId(UUID.randomUUID());
        g.setEmployee(employee);
        return g;
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runAll();

        verify(events, never()).publishEvent(any());
        verify(goals, never()).findDueSoon(any(), any(), any());
    }

    @Test
    void publishesOneReminderPerGoalAcrossBothStages() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(goals.findDueSoon(any(), any(), any())).thenReturn(List.of(goalWithEmployee()));
        when(goals.findOverdue(any(), any()))
                .thenReturn(List.of(goalWithEmployee(), goalWithEmployee()));

        job.runAll();

        verify(events, times(3)).publishEvent(any(GoalEvent.class));
    }

    @Test
    void publishedEventsCarryTheCorrectEventType() {
        ReflectionTestUtils.setField(job, "enabled", true);
        Goal overdue = goalWithEmployee();
        when(goals.findDueSoon(any(), any(), any())).thenReturn(List.of());
        when(goals.findOverdue(any(), any())).thenReturn(List.of(overdue));

        job.runAll();

        ArgumentCaptor<GoalEvent> captor = ArgumentCaptor.forClass(GoalEvent.class);
        verify(events).publishEvent(captor.capture());
        Assertions.assertThat(captor.getValue().eventType()).isEqualTo(GoalEventType.GOAL_OVERDUE);
        Assertions.assertThat(captor.getValue().goalId()).isEqualTo(overdue.getId());
    }
}
