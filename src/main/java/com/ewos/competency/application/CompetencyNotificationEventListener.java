package com.ewos.competency.application;

import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.competency.infrastructure.persistence.DevelopmentPlanRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.notification.domain.NotificationType;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24E — bridges {@link CompetencyEvent}s to the in-app notification inbox, mirroring {@code
 * GoalNotificationEventListener}'s shape. {@code CompetencyEvent}s themselves already existed and
 * were already published by {@link EmployeeCompetencyService} and {@link DevelopmentPlanService} —
 * nothing was listening to them before this class.
 *
 * <p>{@code COMPETENCY_CREATED/UPDATED/DEACTIVATED}, {@code ROLE_COMPETENCY_SET}, {@code
 * PLAN_CREATED/CANCELLED}, and {@code ACTION_ADDED/COMPLETED} are deliberately not notified: the
 * first three are catalog/HR-admin housekeeping with no single employee recipient, and plan
 * creation/cancellation and action bookkeeping are either authored by the employee themself (via
 * self-service) or are covered by the coarser plan-level ACTIVATED/COMPLETED events already wired
 * here.
 */
@Component
public class CompetencyNotificationEventListener {

    private final NotificationService notifications;
    private final EmployeeRepository employees;
    private final CompetencyRepository competencies;
    private final DevelopmentPlanRepository plans;

    public CompetencyNotificationEventListener(
            NotificationService notifications,
            EmployeeRepository employees,
            CompetencyRepository competencies,
            DevelopmentPlanRepository plans) {
        this.notifications = notifications;
        this.employees = employees;
        this.competencies = competencies;
        this.plans = plans;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompetencyEvent(CompetencyEvent event) {
        switch (event.eventType()) {
            case ASSESSMENT_RECORDED ->
                    notifyEmployee(
                            event,
                            NotificationType.COMPETENCY_ASSESSED,
                            "Competency assessed",
                            "Your {{competencyName}} competency was assessed ({{assessmentType}})");
            case PLAN_ACTIVATED ->
                    notifyEmployee(
                            event,
                            NotificationType.DEVPLAN_ACTIVATED,
                            "Development plan activated",
                            "Your development plan is now active: {{planTitle}}");
            case PLAN_COMPLETED ->
                    notifyEmployee(
                            event,
                            NotificationType.DEVPLAN_COMPLETED,
                            "Development plan completed",
                            "Your development plan is complete: {{planTitle}}");
            case ACTION_DUE_REMINDER ->
                    notifyEmployee(
                            event,
                            NotificationType.DEVPLAN_ACTION_DUE,
                            "Development action due soon",
                            "An action on your development plan is due soon: {{planTitle}}");
            case ACTION_OVERDUE ->
                    notifyEmployee(
                            event,
                            NotificationType.DEVPLAN_ACTION_OVERDUE,
                            "Development action overdue",
                            "An action on your development plan is past due: {{planTitle}}");
            default -> {
                // See class javadoc for why the remaining event types are not notification-worthy.
            }
        }
    }

    private void notifyEmployee(
            CompetencyEvent event, NotificationType type, String title, String body) {
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
                Map.of(
                        "competencyName", competencyName(event),
                        "assessmentType", event.detail() == null ? "" : event.detail(),
                        "planTitle", planTitle(event)));
    }

    private String competencyName(CompetencyEvent event) {
        if (event.competencyId() == null) {
            return "a competency";
        }
        return competencies
                .findByIdAndTenantId(event.competencyId(), event.tenantId())
                .map(Competency::getName)
                .orElse("a competency");
    }

    private String planTitle(CompetencyEvent event) {
        if (event.planId() == null) {
            return "your development plan";
        }
        return plans.findByIdAndTenantId(event.planId(), event.tenantId())
                .map(DevelopmentPlan::getTitle)
                .orElse("your development plan");
    }
}
