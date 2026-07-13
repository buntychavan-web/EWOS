package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Organization level definition")
public record OrganizationLevelResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        int displaySequence,
        UUID parentLevelId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
