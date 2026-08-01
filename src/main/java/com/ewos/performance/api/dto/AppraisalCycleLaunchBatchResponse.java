package com.ewos.performance.api.dto;

import com.ewos.performance.domain.AppraisalCycleLaunchBatchStatus;
import java.time.Instant;
import java.util.UUID;

public record AppraisalCycleLaunchBatchResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID cycleId,
        UUID templateId,
        AppraisalCycleLaunchBatchStatus status,
        int totalMatched,
        int totalCreated,
        int totalSkippedExisting,
        int totalFailed,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {}
