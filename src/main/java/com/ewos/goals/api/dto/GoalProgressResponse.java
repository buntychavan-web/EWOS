package com.ewos.goals.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GoalProgressResponse(
        UUID id,
        UUID goalId,
        String currentValue,
        BigDecimal progressPercent,
        String notes,
        Instant recordedAt,
        UUID recordedBy) {}
