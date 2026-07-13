package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Person with its current profile version")
public record PersonResponse(
        UUID id,
        UUID tenantId,
        String groupPersonId,
        boolean active,
        PersonVersionResponse currentVersion,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
