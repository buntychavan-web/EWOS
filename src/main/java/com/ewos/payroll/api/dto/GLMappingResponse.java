package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.AllocationDimension;
import com.ewos.payroll.domain.GLMappingSourceKind;
import java.util.UUID;

public record GLMappingResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        GLMappingSourceKind sourceKind,
        String sourceCode,
        UUID debitAccountId,
        String debitAccountCode,
        UUID creditAccountId,
        String creditAccountCode,
        AllocationDimension allocationDimension,
        String description,
        boolean active,
        long versionNo) {}
