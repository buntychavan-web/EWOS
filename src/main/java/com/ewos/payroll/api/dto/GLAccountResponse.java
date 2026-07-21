package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.GLAccountType;
import java.util.UUID;

public record GLAccountResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        GLAccountType accountType,
        boolean active,
        long versionNo) {}
