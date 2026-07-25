package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.CompanyStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCompanyRequest(
        @Size(max = 256) String name,
        @Pattern(regexp = "^[A-Z]{2}$", message = "countryCode must be ISO-3166-1 alpha-2")
                String countryCode,
        CompanyStatus status) {}
