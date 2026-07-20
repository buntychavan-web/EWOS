package com.ewos.workflow.domain;

/** Kind of principal a workflow task is assigned to. */
public enum WorkflowActorType {
    /** A specific application user by ID. */
    USER,

    /** A specific employee by ID (denormalised so tasks survive user-account changes). */
    EMPLOYEE,

    /** A group by role name — anyone with that role can claim the task. */
    ROLE,

    /** The workflow engine itself; for auto transitions and system-generated closures. */
    SYSTEM
}
