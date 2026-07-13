package com.ewos.company.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Effective-dated profile snapshot payload shared by create and update endpoints. The {@code
 * effectiveFrom} marks the start of the new version's validity window.
 */
@Schema(description = "Effective-dated company profile snapshot.")
public record CompanyProfile(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String legalName,
        @Size(max = 500) String logoUrl,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Min(1) @Max(12) Integer fiscalYearStartMonth,
        @NotNull LocalDate effectiveFrom) {}
