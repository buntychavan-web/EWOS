package com.ewos.integration.api.dto;

import com.ewos.integration.domain.ClientGoLiveStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateClientGoLiveConfigurationRequest(
        LocalDate goLiveDate, ClientGoLiveStatus status, @Size(max = 2048) String notes) {}
