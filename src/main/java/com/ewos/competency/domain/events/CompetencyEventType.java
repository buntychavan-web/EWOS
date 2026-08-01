package com.ewos.competency.domain.events;

/** Competency lifecycle event codes published on {@code ewos.competency.event}. */
public enum CompetencyEventType {
    COMPETENCY_CREATED,
    COMPETENCY_UPDATED,
    COMPETENCY_DEACTIVATED,
    ROLE_COMPETENCY_SET,
    EMPLOYEE_COMPETENCY_UPDATED,
    ASSESSMENT_RECORDED,
    PLAN_CREATED,
    PLAN_ACTIVATED,
    PLAN_COMPLETED,
    PLAN_CANCELLED,
    ACTION_ADDED,
    ACTION_COMPLETED,

    // Sprint 24E — published by DevelopmentActionReminderJob, not by DevelopmentPlanService.
    ACTION_DUE_REMINDER,
    ACTION_OVERDUE
}
