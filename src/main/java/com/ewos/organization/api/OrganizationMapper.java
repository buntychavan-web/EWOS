package com.ewos.organization.api;

import com.ewos.organization.api.dto.OrganizationUnitResponse;
import com.ewos.organization.api.dto.OrganizationUnitTreeNode;
import com.ewos.organization.api.dto.OrganizationUnitTypeResponse;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.domain.OrganizationUnitType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Explicit mapping from Organization aggregates to their API records. No reflection, no
 * boilerplate-generator: greppable field-for-field mapping so schema drift shows up in a diff.
 */
@Component
public final class OrganizationMapper {

    public OrganizationUnitTypeResponse toResponse(OrganizationUnitType type) {
        return new OrganizationUnitTypeResponse(
                type.getId(),
                type.getTenantId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.getSortOrder(),
                type.isActive(),
                type.getCreatedAt(),
                type.getUpdatedAt(),
                type.getCreatedBy(),
                type.getUpdatedBy(),
                type.getVersionNo());
    }

    public OrganizationUnitResponse toResponse(OrganizationUnit unit) {
        OrganizationUnitType type = unit.getUnitType();
        OrganizationUnit parent = unit.getParent();
        return new OrganizationUnitResponse(
                unit.getId(),
                unit.getTenantId(),
                unit.getCompanyId(),
                type != null ? type.getId() : null,
                type != null ? type.getCode() : null,
                parent != null ? parent.getId() : null,
                unit.getCode(),
                unit.getName(),
                unit.getDescription(),
                unit.getCountryCode(),
                unit.getCostCenterCode(),
                unit.getManagerPersonId(),
                unit.getStatus(),
                unit.getEffectiveFrom(),
                unit.getEffectiveTo(),
                unit.getCreatedAt(),
                unit.getUpdatedAt(),
                unit.getCreatedBy(),
                unit.getUpdatedBy(),
                unit.getVersionNo());
    }

    /**
     * Assembles a hierarchical tree from a flat list of units in a single pass. Runs O(n) with two
     * hash lookups per unit; safe for org-charts up to hundreds of thousands of units.
     */
    public List<OrganizationUnitTreeNode> toTree(List<OrganizationUnit> allUnits) {
        Map<UUID, OrganizationUnitTreeNode> byId = new java.util.LinkedHashMap<>();
        Map<UUID, List<OrganizationUnitTreeNode>> childrenByParent =
                new java.util.LinkedHashMap<>();
        for (OrganizationUnit u : allUnits) {
            byId.put(
                    u.getId(),
                    new OrganizationUnitTreeNode(
                            u.getId(),
                            u.getCode(),
                            u.getName(),
                            u.getUnitType() != null ? u.getUnitType().getCode() : null,
                            u.getStatus(),
                            new java.util.ArrayList<>()));
        }
        List<OrganizationUnitTreeNode> roots = new java.util.ArrayList<>();
        for (OrganizationUnit u : allUnits) {
            OrganizationUnitTreeNode node = byId.get(u.getId());
            UUID parentId =
                    Optional.ofNullable(u.getParent()).map(OrganizationUnit::getId).orElse(null);
            if (parentId == null || !byId.containsKey(parentId)) {
                roots.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> byId.get(k).children()).add(node);
            }
        }
        return roots;
    }
}
