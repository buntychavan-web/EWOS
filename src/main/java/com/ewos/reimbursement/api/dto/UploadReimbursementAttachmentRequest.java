package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Copied field-for-field from {@code UploadCandidateDocumentRequest} — metadata only.
 *
 * <p>Deliberately has no {@code ocrAssisted}/{@code ocrRawResponse} fields: those live on {@link
 * com.ewos.reimbursement.domain.ReimbursementClaimAttachment} but must never be client-settable — a
 * caller could otherwise falsely mark any attachment as OCR-assisted or inject fabricated OCR
 * output. Normal uploads through this DTO always persist {@code ocrAssisted=false}/{@code
 * ocrRawResponse=null}; only a trusted server-side OCR execution path (none exists yet — Sprint 2
 * ships {@code NoOpReceiptOcrService} only) may ever set them.
 */
public record UploadReimbursementAttachmentRequest(
        @NotBlank @Size(max = 512) String filename,
        @NotBlank @Size(max = 128) String mimeType,
        @Min(1) long sizeBytes,
        @NotBlank @Size(max = 1024) String storageUri,
        @Size(max = 2000) String notes) {}
