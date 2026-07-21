package com.ewos.recruitment.api.dto;

import com.ewos.recruitment.domain.EmploymentType;
import java.math.BigDecimal;
import java.util.UUID;

public record JobPositionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String title,
        String description,
        UUID departmentOrgUnitId,
        String location,
        EmploymentType employmentType,
        String grade,
        String salaryCurrency,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        boolean active,
        long versionNo) {}
