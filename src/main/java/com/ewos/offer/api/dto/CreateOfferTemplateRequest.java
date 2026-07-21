package com.ewos.offer.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOfferTemplateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @NotBlank String bodyTemplate,
        @Pattern(regexp = "^[A-Z]{3}$") String defaultCurrency,
        @Min(0) Integer defaultNoticePeriodDays,
        @Min(0) Integer defaultProbationDays,
        @Min(1) Integer defaultExpiryDays,
        Boolean active) {}
