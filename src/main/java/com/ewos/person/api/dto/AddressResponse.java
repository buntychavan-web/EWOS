package com.ewos.person.api.dto;

import com.ewos.person.domain.AddressKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Person address")
public record AddressResponse(
        UUID id,
        UUID personId,
        AddressKind addressKind,
        String line1,
        String line2,
        String city,
        String state,
        String country,
        String postalCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
