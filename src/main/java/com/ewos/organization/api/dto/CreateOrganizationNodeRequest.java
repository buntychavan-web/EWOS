package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Create an organization node")
public record CreateOrganizationNodeRequest(
        @Schema(description = "Optional tenant id; defaults to the DEFAULT tenant") UUID tenantId,
        @NotNull UUID levelId,
        @Schema(description = "Parent node id — omit only for a root node") UUID parentNodeId,
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @NotNull LocalDate effectiveFrom) {}
