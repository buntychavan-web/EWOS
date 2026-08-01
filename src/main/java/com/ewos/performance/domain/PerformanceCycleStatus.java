package com.ewos.performance.domain;

/**
 * Lifecycle of a performance cycle. {@code OPEN} is the "Published" stage — appraisals may be
 * launched (individually or via bulk launch) once a cycle reaches it. {@code HR_REVIEW}, {@code
 * FINAL_APPROVAL}, and {@code RELEASED} were added in Sprint 24B, between {@code CALIBRATION} and
 * {@code CLOSED}, to represent the full enterprise review chain; see {@link
 * PerformanceCycleLifecyclePolicy} for the legal transition graph.
 */
public enum PerformanceCycleStatus {
    DRAFT,
    OPEN,
    SELF_REVIEW,
    MANAGER_REVIEW,
    REVIEWER_REVIEW,
    CALIBRATION,
    HR_REVIEW,
    FINAL_APPROVAL,
    RELEASED,
    CLOSED,
    CANCELLED
}
