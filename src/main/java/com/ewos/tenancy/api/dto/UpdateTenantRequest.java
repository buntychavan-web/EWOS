package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.TenantStatus;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(@Size(max = 256) String name, TenantStatus status) {}
