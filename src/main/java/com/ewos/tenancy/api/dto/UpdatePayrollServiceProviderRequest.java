package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ProviderStatus;
import jakarta.validation.constraints.Size;

public record UpdatePayrollServiceProviderRequest(
        @Size(max = 256) String name, ProviderStatus status) {}
