package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        description =
                "Set or replace the current contact channels — creates a new effective-dated row")
public record SetContactRequest(
        @Size(max = 20) String personalMobile,
        @Size(max = 20) String alternateMobile,
        @Email @Size(max = 255) String personalEmail,
        @Email @Size(max = 255) String alternateEmail,
        @NotNull LocalDate effectiveFrom) {}
