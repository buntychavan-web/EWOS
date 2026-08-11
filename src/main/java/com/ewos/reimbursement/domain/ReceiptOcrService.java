package com.ewos.reimbursement.domain;

/**
 * Contract for a receipt-OCR plug-in. Modeled directly on {@code com.ewos.ats.domain.ResumeParser}.
 * The default binding ({@code NoOpReceiptOcrService}) never calls a third-party OCR service and
 * never fabricates a result; deployments that want real extraction ship their own {@code @Primary}
 * bean pointing at a provider.
 *
 * <p>Whatever this returns is a suggestion only, never authoritative — see {@link
 * com.ewos.reimbursement.application.ReimbursementClaimService}, which has no code path that writes
 * an extraction result into a claim. Only the employee's own confirmed create/update request does.
 */
public interface ReceiptOcrService {

    /** Returns {@code true} if this provider can handle the given MIME type. */
    boolean supports(String mimeType);

    /**
     * Extracts suggested fields from a receipt. {@code content} travels inside the caller's own
     * authenticated request — never fetched from a {@code storageUri} (see {@code
     * ReimbursementReceiptExtractionController}). Must not throw for null/empty/unreadable content;
     * an unreadable receipt returns {@link ReceiptOcrResult#EMPTY}, not an error.
     */
    ReceiptOcrResult extract(byte[] content, String mimeType);

    /** Provider identifier + version, returned to the caller for traceability. */
    String providerVersion();
}
