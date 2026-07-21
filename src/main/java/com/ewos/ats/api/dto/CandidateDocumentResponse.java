package com.ewos.ats.api.dto;

import com.ewos.ats.domain.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record CandidateDocumentResponse(
        UUID id,
        UUID candidateId,
        DocumentType documentType,
        String filename,
        String mimeType,
        long sizeBytes,
        String storageUri,
        String notes,
        Instant uploadedAt,
        long versionNo) {}
