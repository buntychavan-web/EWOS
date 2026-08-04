package com.ewos.goals.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.events.GoalEvent;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24E — bridges {@link GoalEvent}s to the in-app notification inbox, mirroring {@code
 * PerformanceNotificationEventListener}'s shape exactly. {@code GoalEvent}s themselves already
 * existed and were already published by every {@link GoalService} lifecycle method (Sprint 22 era)
 * — nothing was listening to them before this class.
 *
 * <p>Goals in EWOS use a score-based review ({@link GoalService#review}), not a binary
 * approve/reject gate — {@code GOAL_REVIEWED} is the closest domain-real event to "goal reviewed",
 * and {@code GOAL_CANCELLED} is the closest to a goal not going forward. {@code GOAL_CREATED},
 * {@code GOAL_UPDATED}, and {@code GOAL_PROGRESS_RECORDED} are authored by the employee themself
 * (or an HR admin still drafting) and are deliberately not notification-worthy.
 */
@Component
public class GoalNotificationEventListener {

    private final NotificationService notifications;
    private final EmployeeRepository employees;
    private final GoalRepository goals;

    public GoalNotificationEventListener(
            NotificationService notifications, EmployeeRepository employees, GoalRepository goals) {
        this.notifications = notifications;
        this.employees = employees;
        this.goals = goals;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGoalEvent(GoalEvent event) {
        switch (event.eventType()) {
            case GOAL_ASSIGNED ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_ASSIGNED,
                            "Goal assigned",
                            "A new goal has been assigned to you: {{goalName}}");
            case GOAL_UNDER_REVIEW ->
                    notifyManager(
                            event,
                            NotificationType.GOAL_REVIEW_PENDING,
                            "Goal review pending",
                            "{{employeeName}} submitted a goal for your review: {{goalName}}");
            case GOAL_REVIEWED ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_REVIEWED,
                            "Goal reviewed",
                            "Your goal has been reviewed: {{goalName}}");
            case GOAL_COMPLETED ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_COMPLETED,
                            "Goal completed",
                            "Your goal has been marked complete: {{goalName}}");
            case GOAL_CANCELLED ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_CANCELLED,
                            "Goal cancelled",
                            "Your goal was cancelled: {{goalName}}");
            case GOAL_DUE_REMINDER ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_DUE_REMINDER,
                            "Goal due soon",
                            "Your goal is due soon: {{goalName}}");
            case GOAL_OVERDUE ->
                    notifyEmployee(
                            event,
                            NotificationType.GOAL_OVERDUE,
                            "Goal overdue",
                            "Your goal is past its due date: {{goalName}}");
            default -> {
                // GOAL_CREATED/UPDATED/PROGRESS_RECORDED, LIBRARY_*: no notification-worthy
                // individual recipient action — see class javadoc.
            }
        }
    }

    private void notifyEmployee(GoalEvent event, NotificationType type, String title, String body) {
        if (event.employeeId() == null) {
            return;
        }
        Employee employee =
                employees.findByIdAndTenantId(event.employeeId(), event.tenantId()).orElse(null);
        if (employee == null || employee.getUserId() == null) {
            return;
        }
        notifications.send(
                event.tenantId(),
                employee.getUserId(),
                type,
                title,
                body,
                null,
                Map.of("goalName", goalName(event)));
    }

    private void notifyManager(GoalEvent event, NotificationType type, String title, String body) {
        if (event.employeeId() == null) {
            return;
        }
        Employee employee =
                employees.findByIdAndTenantId(event.employeeId(), event.tenantId()).orElse(null);
        if (employee == null
                || employee.getManager() == null
                || employee.getManager().getUserId() == null) {
            return;
        }
        notifications.send(
                event.tenantId(),
                employee.getManager().getUserId(),
                type,
                title,
                body,
                null,
                Map.of(
                        "goalName", goalName(event),
                        "employeeName", displayName(employee)));
    }

    private String goalName(GoalEvent event) {
        if (event.goalId() == null) {
            return "a goal";
        }
        return goals.findByIdAndTenantId(event.goalId(), event.tenantId())
                .map(Goal::getName)
                .orElse("a goal");
    }

    private static String displayName(Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? "An employee" : combined;
    }
}
