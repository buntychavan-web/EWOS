package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.workflow.domain.WorkflowDefinition;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.ewos.workflow.domain.WorkflowState;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowHistoryRepository;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WorkflowEscalationSchedulerTest {

    @Mock WorkflowTaskRepository tasks;
    @Mock WorkflowHistoryRepository history;
    @Mock ApplicationEventPublisher events;

    private static WorkflowTask overdueTask() {
        WorkflowState state = new WorkflowState();
        state.setId(UUID.randomUUID());
        state.setCode("REVIEW");
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(UUID.randomUUID());
        instance.setTenantId(UUID.randomUUID());
        instance.setCompanyId(UUID.randomUUID());
        instance.setDefinition(new WorkflowDefinition());
        instance.setCurrentState(state);
        instance.setStatus(WorkflowInstanceStatus.RUNNING);

        WorkflowTask task = new WorkflowTask();
        task.setId(UUID.randomUUID());
        task.setInstance(instance);
        task.setState(state);
        task.setStatus(WorkflowTaskStatus.OPEN);
        task.setDueAt(Instant.now().minusSeconds(3600));
        task.setEscalationLevel(0);
        return task;
    }

    @Test
    void disabledSchedulerNeverScansOrEscalates() {
        WorkflowEscalationScheduler scheduler =
                new WorkflowEscalationScheduler(tasks, history, events, false, 3);

        scheduler.runNow();

        verify(tasks, never()).findOverdueOpenTasks(any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void enabledSchedulerEscalatesOverdueTasksAndEmitsEvent() {
        WorkflowEscalationScheduler scheduler =
                new WorkflowEscalationScheduler(tasks, history, events, true, 3);
        WorkflowTask task = overdueTask();
        when(tasks.findOverdueOpenTasks(any(), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(List.of(task));

        scheduler.runNow();

        assertThat(task.getStatus()).isEqualTo(WorkflowTaskStatus.ESCALATED);
        assertThat(task.getEscalationLevel()).isEqualTo(1);
        assertThat(task.getEscalatedAt()).isNotNull();
        verify(history).save(any());
        ArgumentCaptor<WorkflowEvent> captor = ArgumentCaptor.forClass(WorkflowEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(WorkflowEventType.TASK_ESCALATED);
        assertThat(captor.getValue().taskId()).isEqualTo(task.getId());
    }
}
