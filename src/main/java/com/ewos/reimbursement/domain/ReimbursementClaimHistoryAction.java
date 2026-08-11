package com.ewos.reimbursement.domain;

/**
 * Actions recorded in {@link ReimbursementClaimHistory}. Deliberately does not include {@code
 * UPDATE_DRAFT} — a DRAFT-to-DRAFT edit is not a lifecycle transition, and {@code
 * AuditableEntity}'s own {@code updatedAt}/{@code updatedBy} already cover "who last edited this
 * draft" without needing a history row per edit.
 */
public enum ReimbursementClaimHistoryAction {
    CREATE_DRAFT,
    SUBMIT,
    MANAGER_APPROVE,
    MANAGER_REJECT,
    FINANCE_APPROVE,
    FINANCE_REJECT,
    CANCEL
}
