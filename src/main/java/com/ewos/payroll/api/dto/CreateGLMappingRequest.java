package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.AllocationDimension;
import com.ewos.payroll.domain.GLMappingSourceKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateGLMappingRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull GLMappingSourceKind sourceKind,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$")
                String sourceCode,
        @NotNull UUID debitAccountId,
        @NotNull UUID creditAccountId,
        AllocationDimension allocationDimension,
        @Size(max = 512) String description,
        Boolean active) {}
