package com.ewos.workflow.domain;

/** Lifecycle status of a {@link WorkflowTask}. */
public enum WorkflowTaskStatus {
    /** Newly emitted, no actor has claimed it. */
    OPEN,

    /** An actor has claimed the task but not yet completed. */
    CLAIMED,

    /** Completed with an outcome. */
    COMPLETED,

    /** Cancelled by admin (e.g. instance was cancelled). */
    CANCELLED,

    /** Escalated to a higher approver / role. */
    ESCALATED
}
