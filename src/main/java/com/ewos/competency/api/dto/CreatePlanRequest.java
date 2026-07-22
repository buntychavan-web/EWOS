package com.ewos.competency.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePlanRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotBlank @Size(max = 256) String title,
        @Size(max = 4000) String description,
        LocalDate startsOn,
        LocalDate endsOn) {}
