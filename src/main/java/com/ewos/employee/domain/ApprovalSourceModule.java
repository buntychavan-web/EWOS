package com.ewos.employee.domain;

/**
 * Sprint 27B — which module a unified-approvals-inbox item actually lives in. {@link #LEAVE} and
 * {@link #TIMESHEET} are act-through (their own {@code approve}/{@code reject} already enforce a
 * manager-of-employee relationship, mirrored exactly); {@link #PERFORMANCE}, {@link #PROBATION},
 * and {@link #REQUISITION} are read-only summary cards with a deep link to their own module screen
 * — see {@code ManagerApprovalsService} class javadoc for why.
 */
public enum ApprovalSourceModule {
    LEAVE,
    TIMESHEET,
    PERFORMANCE,
    PROBATION,
    REQUISITION
}
