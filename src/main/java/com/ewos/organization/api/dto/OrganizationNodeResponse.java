package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Organization node")
public record OrganizationNodeResponse(
        UUID id,
        UUID tenantId,
        UUID levelId,
        String levelCode,
        UUID parentNodeId,
        String code,
        String name,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
