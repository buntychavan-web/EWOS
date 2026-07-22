package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateCalibrationSessionRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID cycleId,
        @NotBlank @Size(max = 256) String name,
        Instant scheduledAt,
        UUID facilitatorId,
        @Size(max = 4000) String notes) {}
