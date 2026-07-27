package com.ewos.workflow.domain;

/**
 * How many of a state's concurrently open tasks must complete before the engine advances the
 * instance out of that state.
 */
public enum WorkflowApprovalMode {
    /** Exactly one task is expected; completing it advances the instance (V11 behaviour). */
    SINGLE,

    /**
     * The first task completed advances the instance; any other still-open sibling tasks for the
     * same state are auto-cancelled.
     */
    ANY,

    /**
     * All sibling tasks for the state must complete with the same action code before the instance
     * advances. A task completed with a different action code than its siblings advances
     * immediately via that action's transition (fail-fast — e.g. a single rejection blocks
     * unanimous approval).
     */
    ALL
}
