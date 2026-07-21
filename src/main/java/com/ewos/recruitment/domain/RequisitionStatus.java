package com.ewos.recruitment.domain;

/**
 * Lifecycle of a job requisition.
 *
 * <p>Transitions:
 *
 * <pre>
 *   DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──open──▶ OPEN
 *                                       ──reject──▶ REJECTED
 *   OPEN  ──hold──▶ ON_HOLD ──resume──▶ OPEN
 *   OPEN  ──fill──▶ FILLED
 *   OPEN  ──close──▶ CLOSED
 *   *     ──cancel──▶ CANCELLED  (DRAFT / PENDING_APPROVAL / APPROVED / OPEN / ON_HOLD)
 * </pre>
 */
public enum RequisitionStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    OPEN,
    ON_HOLD,
    FILLED,
    CLOSED,
    CANCELLED
}
