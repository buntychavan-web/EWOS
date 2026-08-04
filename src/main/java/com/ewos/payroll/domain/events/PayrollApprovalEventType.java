package com.ewos.payroll.domain.events;

/** Discriminator for {@link PayrollApprovalEvent}. */
public enum PayrollApprovalEventType {
    /** A run was submitted for approval; level {@code levelNumber}'s approvers must be notified. */
    SUBMITTED,
    /** A level was approved but the request isn't fully approved yet; the next level opens. */
    LEVEL_ADVANCED,
    /** Every level approved; the preparer should be notified (the run is auto-finalized). */
    FULLY_APPROVED,
    /** A level rejected the run; the preparer should be notified. */
    REJECTED
}
