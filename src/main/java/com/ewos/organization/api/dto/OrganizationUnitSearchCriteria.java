package com.ewos.organization.api.dto;

import com.ewos.organization.domain.OrganizationUnitStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrganizationUnitSearchCriteria(
        @NotNull UUID tenantId,
        UUID companyId,
        UUID unitTypeId,
        UUID parentId,
        OrganizationUnitStatus status,
        String code,
        String name,
        String countryCode,
        UUID managerPersonId,
        Boolean rootsOnly) {}
