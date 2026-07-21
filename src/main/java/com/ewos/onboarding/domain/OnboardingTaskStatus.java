package com.ewos.onboarding.domain;

/** Lifecycle of an onboarding task instance. */
public enum OnboardingTaskStatus {
    PENDING,
    IN_PROGRESS,
    WAITING_ON_EMPLOYEE,
    WAITING_ON_COMPANY,
    COMPLETED,
    SKIPPED,
    FAILED
}
