package com.ewos.reimbursement.domain;

/**
 * Lifecycle of a {@link ReimbursementClaim}. {@code FINANCE_APPROVED} means "approved for future
 * payroll processing," not "paid" — no {@code PAID} state exists yet; that is Sprint 3's payroll
 * linkage, deliberately out of scope here. See {@link ReimbursementClaimLifecyclePolicy} for the
 * allowed transitions between these states.
 */
public enum ReimbursementClaimStatus {
    DRAFT,
    SUBMITTED,
    MANAGER_APPROVED,
    FINANCE_APPROVED,
    MANAGER_REJECTED,
    FINANCE_REJECTED,
    CANCELLED
}
