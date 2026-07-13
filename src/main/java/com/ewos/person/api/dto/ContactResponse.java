package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Person contact channels")
public record ContactResponse(
        UUID id,
        UUID personId,
        String personalMobile,
        String alternateMobile,
        String personalEmail,
        String alternateEmail,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
