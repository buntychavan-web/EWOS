package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code companyId} null applies the configuration tenant-wide; a non-null value overrides it for
 * that one company. {@code blockDurationYears}/{@code anchorBlockStartYear} are configurable by
 * design (Sprint 24K §8.1) so a future government change to the block length or boundary never
 * requires a code change — only a new configuration row.
 */
public record CreateLtaBlockConfigurationRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @Min(1) Integer blockDurationYears,
        @NotNull Integer anchorBlockStartYear,
        @Min(1) Integer maxExemptClaimsPerBlock,
        Boolean carryForwardEnabled,
        @Min(0) Integer carryForwardMaxClaims,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 2000) String notes) {}
