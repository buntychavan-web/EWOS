package com.ewos.onboarding.domain.events;

/** Onboarding lifecycle event codes published on {@code ewos.onboarding.event}. */
public enum OnboardingEventType {
    PLAN_CREATED,
    PLAN_STARTED,
    PLAN_UPDATED,
    PLAN_COMPLETED,
    PLAN_CANCELLED,
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STARTED,
    TASK_COMPLETED,
    TASK_SKIPPED,
    TASK_FAILED,
    TASK_REMINDER_SENT,
    SURVEY_SUBMITTED,
    BUDDY_ASSIGNED,
    MANAGER_ASSIGNED
}
