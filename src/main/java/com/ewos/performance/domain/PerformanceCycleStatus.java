package com.ewos.performance.domain;

/** Lifecycle of a performance cycle. */
public enum PerformanceCycleStatus {
    DRAFT,
    OPEN,
    SELF_REVIEW,
    MANAGER_REVIEW,
    REVIEWER_REVIEW,
    CALIBRATION,
    CLOSED,
    CANCELLED
}
