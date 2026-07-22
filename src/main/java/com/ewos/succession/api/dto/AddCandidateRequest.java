package com.ewos.succession.api.dto;

import com.ewos.succession.domain.ReadinessLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddCandidateRequest(
        @NotNull UUID employeeId,
        @Min(1) int priority,
        @NotNull ReadinessLevel readiness,
        @Size(max = 2000) String notes) {}
