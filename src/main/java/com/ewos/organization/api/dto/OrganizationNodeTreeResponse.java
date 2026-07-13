package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Node plus its subtree")
public record OrganizationNodeTreeResponse(
        UUID id,
        UUID levelId,
        String levelCode,
        String code,
        String name,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<OrganizationNodeTreeResponse> children) {}
