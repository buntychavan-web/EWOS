package com.ewos.notification.application;

import com.ewos.notification.domain.NotificationType;
import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowInstance;
import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.events.WorkflowEvent;
import com.ewos.workflow.infrastructure.persistence.WorkflowInstanceRepository;
import com.ewos.workflow.infrastructure.persistence.WorkflowTaskRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges workflow-engine events to the in-app notification inbox. Task-level events resolve their
 * recipient by loading the task (only {@code USER}/{@code EMPLOYEE} assignees have a single
 * unambiguous recipient — a {@code ROLE}-assigned task's holders are the owning module's concern,
 * not the engine's). Instance-level events notify {@code WorkflowInstance.createdBy} — the user who
 * started the instance — reusing the audit field Spring Data auditing already populates rather than
 * adding a "requester" concept to the engine.
 */
@Component
public class WorkflowNotificationListener {

    private final NotificationService notifications;
    private final WorkflowTaskRepository tasks;
    private final WorkflowInstanceRepository instances;

    public WorkflowNotificationListener(
            NotificationService notifications,
            WorkflowTaskRepository tasks,
            WorkflowInstanceRepository instances) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.instances = instances;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWorkflowEvent(WorkflowEvent event) {
        switch (event.eventType()) {
            case TASK_ASSIGNED ->
                    notifyTaskAssignee(
                            event,
                            NotificationType.TASK_ASSIGNED,
                            "New task assigned",
                            "A workflow task is waiting on your action" + stateSuffix(event));
            case TASK_ESCALATED ->
                    notifyTaskAssignee(
                            event,
                            NotificationType.TASK_ESCALATED,
                            "Task overdue",
                            "Your workflow task passed its due date and was escalated"
                                    + stateSuffix(event));
            case INSTANCE_COMPLETED ->
                    notifyRequester(
                            event,
                            NotificationType.INSTANCE_COMPLETED,
                            "Request completed",
                            "Your workflow request reached " + event.toStateCode());
            case INSTANCE_CANCELLED ->
                    notifyRequester(
                            event,
                            NotificationType.INSTANCE_CANCELLED,
                            "Request cancelled",
                            "Your workflow request was cancelled");
            case INSTANCE_ERRORED ->
                    notifyRequester(
                            event,
                            NotificationType.INSTANCE_ERRORED,
                            "Request errored",
                            "Your workflow request hit an error and needs administrator attention");
            default -> {
                // DEFINITION_PUBLISHED / INSTANCE_STARTED / STATE_ENTERED / TASK_COMPLETED: no
                // unambiguous single recipient at the engine level.
            }
        }
    }

    private void notifyTaskAssignee(
            WorkflowEvent event, NotificationType type, String title, String body) {
        if (event.taskId() == null) {
            return;
        }
        tasks.findById(event.taskId())
                .filter(
                        t ->
                                t.getAssigneeActorType() == WorkflowActorType.USER
                                        || t.getAssigneeActorType() == WorkflowActorType.EMPLOYEE)
                .ifPresent(
                        (WorkflowTask t) ->
                                notifications.send(
                                        event.tenantId(),
                                        t.getAssigneeActorId(),
                                        type,
                                        title,
                                        body,
                                        null));
    }

    private void notifyRequester(
            WorkflowEvent event, NotificationType type, String title, String body) {
        if (event.instanceId() == null) {
            return;
        }
        UUID recipient =
                instances
                        .findById(event.instanceId())
                        .map(WorkflowInstance::getCreatedBy)
                        .orElse(null);
        notifications.send(event.tenantId(), recipient, type, title, body, null);
    }

    private static String stateSuffix(WorkflowEvent event) {
        return event.toStateCode() != null ? " (" + event.toStateCode() + ")" : "";
    }
}
