package com.ewos.workflow.domain;

/** Lifecycle status of a {@link WorkflowInstance}. */
public enum WorkflowInstanceStatus {
    /** In progress. Awaiting tasks or automatic transitions. */
    RUNNING,

    /** Reached a terminal state successfully. Immutable. */
    COMPLETED,

    /** Explicitly cancelled by an admin action. Immutable. */
    CANCELLED,

    /** Halted due to an unrecoverable error (guard failure, missing transition, ...). */
    ERROR
}
