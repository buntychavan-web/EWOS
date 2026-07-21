package com.ewos.offer.domain.preboarding;

/** Lifecycle of a single pre-boarding task instance. */
public enum PreboardingTaskStatus {
    PENDING,
    IN_PROGRESS,
    WAITING_ON_CANDIDATE,
    WAITING_ON_COMPANY,
    COMPLETED,
    SKIPPED,
    FAILED
}
