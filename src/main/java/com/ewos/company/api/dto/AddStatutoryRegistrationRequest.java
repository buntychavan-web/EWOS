package com.ewos.company.api.dto;

import com.ewos.company.domain.StatutoryRegistrationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AddStatutoryRegistrationRequest(
        @NotNull StatutoryRegistrationKind kind,
        @NotBlank @Size(max = 50) String registrationNumber,
        @Size(max = 100) String jurisdiction,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
