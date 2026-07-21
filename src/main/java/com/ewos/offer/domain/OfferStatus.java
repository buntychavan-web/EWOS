package com.ewos.offer.domain;

/**
 * Lifecycle of an {@link Offer}.
 *
 * <pre>
 *   DRAFT ─submit─▶ PENDING_APPROVAL ─approve─▶ APPROVED ─extend─▶ EXTENDED
 *                                     ─reject─▶ REJECTED
 *   EXTENDED ─accept─▶ ACCEPTED
 *   EXTENDED ─decline─▶ DECLINED
 *   EXTENDED ─revise─▶ REVISED   (new offer row with version+1)
 *   EXTENDED ─expire─▶ EXPIRED   (past expires_at)
 *   * ─withdraw─▶ WITHDRAWN     (from any non-terminal state)
 * </pre>
 */
public enum OfferStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXTENDED,
    ACCEPTED,
    DECLINED,
    REVISED,
    EXPIRED,
    WITHDRAWN
}
