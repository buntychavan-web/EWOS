package com.ewos.integration.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClientGoLiveConfigurationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID clientId,
        @NotNull UUID companyId,
        LocalDate goLiveDate,
        @Size(max = 2048) String notes) {}
