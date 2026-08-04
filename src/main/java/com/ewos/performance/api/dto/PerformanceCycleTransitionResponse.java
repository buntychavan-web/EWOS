package com.ewos.performance.api.dto;

import com.ewos.performance.domain.PerformanceCycleStatus;
import java.time.Instant;
import java.util.UUID;

public record PerformanceCycleTransitionResponse(
        UUID id,
        UUID cycleId,
        PerformanceCycleStatus fromStatus,
        PerformanceCycleStatus toStatus,
        String notes,
        UUID transitionedBy,
        Instant transitionedAt) {}
