package com.ewos.learning.domain;

/** Lifecycle of a scheduled training session. */
public enum TrainingSessionStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
