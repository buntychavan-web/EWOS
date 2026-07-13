package com.ewos.person.api.dto;

import com.ewos.person.domain.AddressKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Add a permanent / current / communication address")
public record AddAddressRequest(
        @NotNull AddressKind addressKind,
        @NotBlank @Size(max = 255) String line1,
        @Size(max = 255) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @Size(max = 30) String postalCode,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
