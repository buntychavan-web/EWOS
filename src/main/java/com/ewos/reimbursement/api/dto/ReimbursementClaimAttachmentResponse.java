package com.ewos.reimbursement.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReimbursementClaimAttachmentResponse(
        UUID id,
        String filename,
        String mimeType,
        long sizeBytes,
        String storageUri,
        String notes,
        boolean ocrAssisted,
        Instant uploadedAt) {}
