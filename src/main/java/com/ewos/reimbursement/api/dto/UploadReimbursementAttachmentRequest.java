package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Copied field-for-field from {@code UploadCandidateDocumentRequest} — metadata only. */
public record UploadReimbursementAttachmentRequest(
        @NotBlank @Size(max = 512) String filename,
        @NotBlank @Size(max = 128) String mimeType,
        @Min(1) long sizeBytes,
        @NotBlank @Size(max = 1024) String storageUri,
        @Size(max = 2000) String notes,
        boolean ocrAssisted,
        String ocrRawResponse) {}
