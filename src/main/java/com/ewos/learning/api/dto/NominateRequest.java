package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NominateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID courseId,
        @NotNull UUID employeeId,
        UUID sessionId,
        UUID learningPathId) {}
