package com.ewos.organization.api.dto;

import com.ewos.organization.domain.OrganizationUnitStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * A single node in the hierarchical response returned by {@code GET
 * /api/v1/organization/units/tree}. Children are inlined so the full sub-tree can be rendered by
 * the client without additional round-trips.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizationUnitTreeNode(
        UUID id,
        String code,
        String name,
        String unitTypeCode,
        OrganizationUnitStatus status,
        List<OrganizationUnitTreeNode> children) {}
