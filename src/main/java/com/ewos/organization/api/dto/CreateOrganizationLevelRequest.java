package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Create a configurable organization level")
public record CreateOrganizationLevelRequest(
        @Schema(description = "Optional tenant id; defaults to the DEFAULT tenant") UUID tenantId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive Integer displaySequence,
        @Schema(description = "Optional parent level id — omit for the top level")
                UUID parentLevelId,
        @NotNull LocalDate effectiveFrom) {}
