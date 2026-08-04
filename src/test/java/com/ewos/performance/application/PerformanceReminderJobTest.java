package com.ewos.performance.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PerformanceReminderJobTest {

    @Mock AppraisalRepository appraisals;
    @Mock ApplicationEventPublisher events;

    private PerformanceReminderJob job;

    @BeforeEach
    void setUp() {
        job = new PerformanceReminderJob(appraisals, events);
        ReflectionTestUtils.setField(job, "batchSize", 5000);
    }

    private Appraisal appraisalWithCycle() {
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId(UUID.randomUUID());
        Employee employee = new Employee();
        employee.setId(UUID.randomUUID());
        Appraisal a = new Appraisal();
        a.setId(UUID.randomUUID());
        a.setTenantId(UUID.randomUUID());
        a.setCompanyId(UUID.randomUUID());
        a.setCycle(cycle);
        a.setEmployee(employee);
        return a;
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runAll();

        verify(events, never()).publishEvent(any());
        verify(appraisals, never()).findOverdueSelfReviews(any(), any());
    }

    @Test
    void publishesOneReminderPerOverdueAppraisalAcrossAllThreeStages() {
        ReflectionTestUtils.setField(job, "enabled", true);
        when(appraisals.findOverdueSelfReviews(any(), any()))
                .thenReturn(List.of(appraisalWithCycle()));
        when(appraisals.findOverdueManagerReviews(any(), any()))
                .thenReturn(List.of(appraisalWithCycle(), appraisalWithCycle()));
        when(appraisals.findOverdueReviewerReviews(any(), any())).thenReturn(List.of());

        job.runAll();

        verify(events, times(3)).publishEvent(any(PerformanceEvent.class));
    }

    @Test
    void publishedEventsCarryTheCorrectEventType() {
        ReflectionTestUtils.setField(job, "enabled", true);
        Appraisal overdue = appraisalWithCycle();
        when(appraisals.findOverdueSelfReviews(any(), any())).thenReturn(List.of(overdue));
        when(appraisals.findOverdueManagerReviews(any(), any())).thenReturn(List.of());
        when(appraisals.findOverdueReviewerReviews(any(), any())).thenReturn(List.of());

        job.runAll();

        org.mockito.ArgumentCaptor<PerformanceEvent> captor =
                org.mockito.ArgumentCaptor.forClass(PerformanceEvent.class);
        verify(events).publishEvent(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().eventType())
                .isEqualTo(PerformanceEventType.SELF_REVIEW_REMINDER);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().appraisalId())
                .isEqualTo(overdue.getId());
    }
}
