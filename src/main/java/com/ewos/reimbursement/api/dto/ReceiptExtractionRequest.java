package com.ewos.reimbursement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Receipt bytes travel inside this request, base64-encoded — never fetched from a {@code
 * storageUri} by the backend (see {@code ReceiptOcrService} javadoc). Mirrors how {@code
 * UploadResumeRequest.rawTextForParsing} carries pre-extracted content directly in the request body
 * rather than the backend reaching out to fetch it.
 */
public record ReceiptExtractionRequest(
        @NotBlank @Size(max = 128) String mimeType, @NotBlank String contentBase64) {}
