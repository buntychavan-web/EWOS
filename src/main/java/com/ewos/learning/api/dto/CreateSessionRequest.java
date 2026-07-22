package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID courseId,
        @NotBlank @Size(max = 256) String name,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 512) String venue,
        @Size(max = 256) String trainerName,
        UUID trainerEmployeeId,
        Integer capacity,
        @Size(max = 2000) String notes) {}
