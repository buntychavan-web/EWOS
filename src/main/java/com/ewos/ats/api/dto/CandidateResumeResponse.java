package com.ewos.ats.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CandidateResumeResponse(
        UUID id,
        UUID candidateId,
        String filename,
        String mimeType,
        long sizeBytes,
        String storageUri,
        boolean primary,
        boolean parsed,
        Instant parsedAt,
        String parserVersion,
        Instant uploadedAt,
        long versionNo) {}
