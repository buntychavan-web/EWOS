package com.ewos.exit.domain;

/**
 * How a resignation/separation record was initiated — Sprint 26 Exit Management V1. Determines
 * which submission path is allowed to create it: {@link #SELF_RESIGNATION} is only ever set by
 * {@code ExitSelfService} (the employee's own submission, server-resolved employee id); every other
 * value requires {@code EXIT_WRITE} on the HR/manager-facing {@code POST /api/v1/exit/resignations}
 * endpoint and is rejected on the self-service path.
 */
public enum ResignationType {
    /** Employee-initiated, submitted through self-service. */
    SELF_RESIGNATION,
    /** HR records a separation on the employee's behalf. */
    HR_INITIATED,
    /** Reporting manager initiates a separation. */
    MANAGER_SEPARATION,
    /** Superannuation / retirement. */
    RETIREMENT,
    /** Involuntary termination. */
    TERMINATION,
    /** Death-in-service case. */
    DEATH,
    /** Employee stopped reporting without formal resignation. */
    ABSCONDING
}
