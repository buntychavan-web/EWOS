package com.ewos.workflow.application;

import com.ewos.workflow.domain.WorkflowHistory;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.domain.events.WorkflowEventType;
import com.ewos.workflow.infrastructure.persistence.WorkflowHistoryRepository;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLA breach sweep — the deferred "SLA breach scheduler" from the V11 module notes. Runs disabled
 * by default (same convention as {@code PurgeJob}) so no environment starts escalating tasks
 * without an operator opting in.
 *
 * <p>An overdue {@code OPEN}/{@code CLAIMED} task (past {@code dueAt}) is moved to {@code
 * ESCALATED} — a status the schema has allowed since V11 but nothing previously set — its
 * escalation level is bumped, a {@code WorkflowHistory} row records it, and a {@code
 * TASK_ESCALATED} event fires (picked up by {@code WorkflowNotificationListener}). Escalation is
 * capped at {@link #maxEscalationLevel}; a task at the cap is left alone rather than escalated
 * again, so it surfaces for manual attention instead of looping forever.
 *
 * <p>Deliberately does not auto-reassign the task to a "next level" approver: the engine has no
 * generic way to know who that is for an arbitrary subject type (see {@code ApproverResolver}'s
 * javadoc — role resolution needs a subject employee id the scheduler doesn't have). Escalation
 * here means "flag it and notify", not "silently reroute it" — reassignment stays a human, {@code
 * WF_ADMIN}, or owning-module decision.
 *
 * <p>Runs outside any tenant's request context, so it reads/writes task repositories directly
 * rather than through {@code WorkflowTaskService} — the same pattern {@code PurgeJob} uses to
 * bypass {@code ClientAccessGuard}, which is meaningless without an authenticated caller.
 */
@Component
public class WorkflowEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEscalationScheduler.class);

    private final WorkflowTaskRepository tasks;
    private final WorkflowHistoryRepository history;
    private final ApplicationEventPublisher events;
    private final boolean enabled;
    private final int maxEscalationLevel;

    public WorkflowEscalationScheduler(
            WorkflowTaskRepository tasks,
            WorkflowHistoryRepository history,
            ApplicationEventPublisher events,
            @Value("${app.workflow.escalation.enabled:false}") boolean enabled,
            @Value("${app.workflow.escalation.max-level:3}") int maxEscalationLevel) {
        this.tasks = tasks;
        this.history = history;
        this.events = events;
        this.enabled = enabled;
        this.maxEscalationLevel = maxEscalationLevel;
    }

    @Scheduled(
            fixedDelayString = "${app.workflow.escalation.interval-ms:900000}",
            initialDelayString = "${app.workflow.escalation.initial-delay-ms:900000}")
    @Transactional
    public void sweep() {
        if (!enabled) {
            return;
        }
        List<WorkflowTask> overdue = tasks.findOverdueOpenTasks(Instant.now(), maxEscalationLevel);
        for (WorkflowTask task : overdue) {
            escalate(task);
        }
        if (!overdue.isEmpty()) {
            log.info("Escalated {} overdue workflow task(s)", overdue.size());
        }
    }

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects flags. */
    @Transactional
    public void runNow() {
        sweep();
    }

    private void escalate(WorkflowTask task) {
        Instant now = Instant.now();
        task.setStatus(WorkflowTaskStatus.ESCALATED);
        task.setEscalatedAt(now);
        task.setEscalationLevel(task.getEscalationLevel() + 1);

        WorkflowInstance instance = task.getInstance();
        WorkflowHistory row = new WorkflowHistory();
        row.setInstance(instance);
        row.setFromState(instance.getCurrentState());
        row.setToState(instance.getCurrentState());
        row.setActionCode("ESCALATE");
        row.setNotes("Task overdue (level " + task.getEscalationLevel() + ")");
        row.setOccurredAt(now);
        history.save(row);

        events.publishEvent(
                new WorkflowEvent(
                        WorkflowEventType.TASK_ESCALATED,
                        instance.getId(),
                        instance.getDefinition() != null ? instance.getDefinition().getId() : null,
                        instance.getTenantId(),
                        instance.getCompanyId(),
                        instance.getSubjectType(),
                        instance.getSubjectId(),
                        instance.getCurrentState().getCode(),
                        instance.getCurrentState().getCode(),
                        "ESCALATE",
                        task.getId(),
                        null,
                        now));
    }
}
