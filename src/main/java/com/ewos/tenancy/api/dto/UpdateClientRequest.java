package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ClientStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateClientRequest(
        @Size(max = 256) String legalName, ClientStatus status, LocalDate onboardedAt) {}
