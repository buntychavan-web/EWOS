package com.ewos.company.api.dto;

import com.ewos.company.domain.StatutoryRegistrationKind;
import java.time.LocalDate;
import java.util.UUID;

public record StatutoryRegistrationResponse(
        UUID id,
        UUID companyId,
        StatutoryRegistrationKind kind,
        String registrationNumber,
        String jurisdiction,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
