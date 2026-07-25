package com.ewos.integration.api.dto;

import com.ewos.integration.domain.ClientGoLiveStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientGoLiveConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID clientId,
        UUID companyId,
        LocalDate goLiveDate,
        ClientGoLiveStatus status,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
