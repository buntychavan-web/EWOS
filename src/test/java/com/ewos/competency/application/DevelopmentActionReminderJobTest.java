package com.ewos.competency.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.competency.domain.DevelopmentAction;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.DevelopmentActionRepository;
import com.ewos.employee.domain.Employee;
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
class DevelopmentActionReminderJobTest {

    @Mock DevelopmentActionRepository actions;
    @Mock ApplicationEventPublisher events;

    private DevelopmentActionReminderJob job;

    @BeforeEach
    void setUp() {
        job = new DevelopmentActionReminderJob(actions, events);
        ReflectionTestUtils.setField(job, "batchSize", 5000);
        ReflectionTestUtils.setField(job, "dueSoonDays", 3);
    }

    private DevelopmentAction actionWithPlan() {
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        DevelopmentPlan plan = new DevelopmentPlan();
        plan.setId(UUID.randomUUID());
        plan.setTenantId(UUID.randomUUID());
        plan.setCompanyId(UUID.randomUUID());
        plan.setEmployee(employee);
        DevelopmentAction a = new DevelopmentAction();
        a.setId(UUID.randomUUID());
        a.setTenantId(plan.getTenantId());
        a.setPlan(plan);
        return a;
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runAll();

        verify(events, never()).publishEvent(any());
        verify(actions, never()).findDueSoon(any(), any(), any());
    }

    @Test
    void publishesOneReminderPerActionAcrossBothStages() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(actions.findDueSoon(any(), any(), any())).thenReturn(List.of(actionWithPlan()));
        when(actions.findOverdue(any(), any()))
                .thenReturn(List.of(actionWithPlan(), actionWithPlan()));

        job.runAll();

        verify(events, times(3)).publishEvent(any(CompetencyEvent.class));
    }

    @Test
    void skipsActionsWithNoPlanOrNoEmployee() {
        ReflectionTestUtils.setField(job, "enabled", true);
        DevelopmentAction orphan = new DevelopmentAction();
        orphan.setId(UUID.randomUUID());
        orphan.setTenantId(UUID.randomUUID());
        when(actions.findDueSoon(any(), any(), any())).thenReturn(List.of(orphan));
        when(actions.findOverdue(any(), any())).thenReturn(List.of());

        job.runAll();

        verify(events, never()).publishEvent(any());
    }

    @Test
    void publishedEventsCarryTheCorrectEventType() {
        ReflectionTestUtils.setField(job, "enabled", true);
        DevelopmentAction overdue = actionWithPlan();
        when(actions.findDueSoon(any(), any(), any())).thenReturn(List.of());
        when(actions.findOverdue(any(), any())).thenReturn(List.of(overdue));

        job.runAll();

        ArgumentCaptor<CompetencyEvent> captor = ArgumentCaptor.forClass(CompetencyEvent.class);
        verify(events).publishEvent(captor.capture());
        Assertions.assertThat(captor.getValue().eventType())
                .isEqualTo(CompetencyEventType.ACTION_OVERDUE);
        Assertions.assertThat(captor.getValue().planId()).isEqualTo(overdue.getPlan().getId());
    }
}
