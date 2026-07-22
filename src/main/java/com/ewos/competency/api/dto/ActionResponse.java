package com.ewos.competency.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ActionResponse(
        UUID id,
        UUID planId,
        UUID competencyId,
        String action,
        LocalDate dueOn,
        Instant completedAt,
        boolean completed,
        String notes) {}
