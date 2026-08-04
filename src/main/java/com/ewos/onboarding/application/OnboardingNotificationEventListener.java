package com.ewos.onboarding.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.domain.events.OnboardingEvent;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskInstanceRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24F — bridges {@link OnboardingEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape. {@code OnboardingEvent}s themselves already
 * existed and were already published by {@link OnboardingPlanService} — nothing was listening to
 * them before this class (the only prior consumer, {@code
 * com.ewos.probation.application.OnboardingPlanCompletedListener}, reacts to {@code PLAN_COMPLETED}
 * to auto-open a probation record; it does not notify anyone, and this listener does not touch that
 * side effect — both simply react to the same event independently). This also closes the specific
 * gap the Sprint 24F CTO readiness review flagged: {@code OnboardingPlanService#remindTask} only
 * published {@code TASK_REMINDER_SENT} with nothing consuming it.
 *
 * <p>{@code PLAN_CREATED/STARTED/UPDATED/CANCELLED} and {@code TASK_CREATED/STARTED/COMPLETED/
 * SKIPPED/FAILED} are authored by the assignee or an HR admin themself. {@code SURVEY_SUBMITTED} is
 * an HR-admin reporting concern, not one employee's action. {@code BUDDY_ASSIGNED}/{@code
 * MANAGER_ASSIGNED} have no notification-worthy individual recipient at this layer: the event
 * carries the onboardee's {@code employeeId}, not the assigned buddy/manager's own identity.
 */
@Component
public class OnboardingNotificationEventListener {

    private final NotificationService notifications;
    private final OnboardingTaskInstanceRepository tasks;
    private final EmployeeRepository employees;

    public OnboardingNotificationEventListener(
            NotificationService notifications,
            OnboardingTaskInstanceRepository tasks,
            EmployeeRepository employees) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.employees = employees;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOnboardingEvent(OnboardingEvent event) {
        switch (event.eventType()) {
            case TASK_ASSIGNED ->
                    notifyAssignee(
                            event,
                            NotificationType.ONBOARDING_TASK_ASSIGNED,
                            "Onboarding task assigned",
                            "An onboarding task has been assigned to you: {{taskName}}");
            case TASK_REMINDER_SENT ->
                    notifyAssignee(
                            event,
                            NotificationType.ONBOARDING_TASK_REASSIGNED,
                            "Onboarding task reminder",
                            "Reminder: an onboarding task is still pending: {{taskName}}");
            case PLAN_COMPLETED ->
                    notifyOnboardee(
                            event,
                            NotificationType.ONBOARDING_PLAN_COMPLETED,
                            "Onboarding complete",
                            "Your onboarding plan is complete — welcome aboard!");
            default -> {
                // See class javadoc for why the remaining event types are not notification-worthy.
            }
        }
    }

    private void notifyAssignee(
            OnboardingEvent event, NotificationType type, String title, String body) {
        if (event.taskId() == null) {
            return;
        }
        tasks.findByIdAndTenantId(event.taskId(), event.tenantId())
                .map(OnboardingTaskInstance::getAssignedEmployee)
                .map(Employee::getUserId)
                .ifPresent(
                        userId ->
                                notifications.send(
                                        event.tenantId(),
                                        userId,
                                        type,
                                        title,
                                        body,
                                        null,
                                        Map.of("taskName", taskName(event))));
    }

    private void notifyOnboardee(
            OnboardingEvent event, NotificationType type, String title, String body) {
        if (event.employeeId() == null) {
            return;
        }
        employees
                .findByIdAndTenantId(event.employeeId(), event.tenantId())
                .map(Employee::getUserId)
                .ifPresent(
                        userId ->
                                notifications.send(
                                        event.tenantId(), userId, type, title, body, null));
    }

    private String taskName(OnboardingEvent event) {
        return tasks.findByIdAndTenantId(event.taskId(), event.tenantId())
                .map(OnboardingTaskInstance::getName)
                .orElse("an onboarding task");
    }
}
