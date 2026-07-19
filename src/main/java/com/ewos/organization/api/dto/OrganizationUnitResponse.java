package com.ewos.organization.api.dto;

import com.ewos.organization.domain.OrganizationUnitStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizationUnitResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID unitTypeId,
        String unitTypeCode,
        UUID parentId,
        String code,
        String name,
        String description,
        String countryCode,
        String costCenterCode,
        UUID managerPersonId,
        OrganizationUnitStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
