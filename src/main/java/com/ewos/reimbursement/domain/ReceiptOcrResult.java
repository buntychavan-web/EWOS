package com.ewos.reimbursement.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Result of a receipt-OCR extraction attempt. Every field maps directly onto an existing, approved
 * {@link ReimbursementClaim} field — no subtotal/tax/invoice-number/line-item fields, since none of
 * those have a corresponding persisted column (see the Sprint 2 OCR architecture addendum). This is
 * a transient DTO only: nothing here is ever persisted as such — a no-op or failed extraction
 * returns {@link #EMPTY}, and only the employee's own confirmed request ever writes a claim's real
 * field values.
 *
 * @param merchantName informational only — no dedicated claim column; useful for the employee to
 *     copy into {@code description} if they choose.
 * @param billDate maps to {@code ReimbursementClaim.claimDate}.
 * @param totalAmount maps to {@code ReimbursementClaim.amount}.
 * @param currency maps to {@code ReimbursementClaim.currency}.
 * @param suggestedCategoryId maps to {@code ReimbursementClaim.category} — a suggestion the
 *     employee picks or overrides, fuzzy-matched by the caller against the tenant's configured
 *     {@link ReimbursementCategory} list.
 * @param fieldConfidence per-field confidence in {@code [0.0, 1.0]}, keyed by field name — empty
 *     when the concrete provider doesn't support it; never fabricated.
 * @param rawProviderResponse opaque provider JSON, kept only for audit/debugging if the caller
 *     chooses to persist it on {@code ReimbursementClaimAttachment.ocrRawResponse}; never parsed
 *     back or re-read to populate anything automatically.
 */
public record ReceiptOcrResult(
        String merchantName,
        LocalDate billDate,
        BigDecimal totalAmount,
        String currency,
        UUID suggestedCategoryId,
        Map<String, Double> fieldConfidence,
        String rawProviderResponse) {

    public static final ReceiptOcrResult EMPTY =
            new ReceiptOcrResult(null, null, null, null, null, Map.of(), null);
}
