package com.ewos.organization.application;

import com.ewos.organization.api.dto.InheritanceOverrideResponse;
import com.ewos.organization.api.dto.NodeVersionResponse;
import com.ewos.organization.api.dto.OrganizationLevelResponse;
import com.ewos.organization.api.dto.OrganizationNodeResponse;
import com.ewos.organization.api.dto.OrganizationNodeTreeResponse;
import com.ewos.organization.domain.OrganizationInheritanceOverride;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.domain.OrganizationNodeVersion;
import java.util.List;

/** Pure entity → response mappers for the organization module. */
final class OrganizationMapper {

    private OrganizationMapper() {}

    static OrganizationLevelResponse toLevel(OrganizationLevel l) {
        return new OrganizationLevelResponse(
                l.getId(),
                l.getTenant().getId(),
                l.getCode(),
                l.getName(),
                l.getDisplaySequence(),
                l.getParentLevel() == null ? null : l.getParentLevel().getId(),
                l.getEffectiveFrom(),
                l.getEffectiveTo(),
                l.isActive(),
                l.getCreatedAt(),
                l.getUpdatedAt(),
                l.getCreatedBy(),
                l.getUpdatedBy(),
                l.getVersion());
    }

    static OrganizationNodeResponse toNode(OrganizationNode n) {
        return new OrganizationNodeResponse(
                n.getId(),
                n.getTenant().getId(),
                n.getLevel().getId(),
                n.getLevel().getCode(),
                n.getParent() == null ? null : n.getParent().getId(),
                n.getCode(),
                n.getName(),
                n.getEffectiveFrom(),
                n.getEffectiveTo(),
                n.isActive(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getCreatedBy(),
                n.getUpdatedBy(),
                n.getVersion());
    }

    static OrganizationNodeTreeResponse toTree(
            OrganizationNode n, List<OrganizationNodeTreeResponse> children) {
        return new OrganizationNodeTreeResponse(
                n.getId(),
                n.getLevel().getId(),
                n.getLevel().getCode(),
                n.getCode(),
                n.getName(),
                n.isActive(),
                n.getEffectiveFrom(),
                n.getEffectiveTo(),
                children);
    }

    static NodeVersionResponse toVersion(OrganizationNodeVersion v) {
        return new NodeVersionResponse(
                v.getId(),
                v.getNode().getId(),
                v.getChangeType(),
                v.getEffectiveFrom(),
                v.getEffectiveTo(),
                v.getSnapshotCode(),
                v.getSnapshotName(),
                v.getSnapshotParentId(),
                v.getSnapshotLevelId(),
                v.getRelatedNodeId(),
                v.getNotes(),
                v.getCreatedAt(),
                v.getCreatedBy());
    }

    static InheritanceOverrideResponse toOverride(OrganizationInheritanceOverride o) {
        return new InheritanceOverrideResponse(
                o.getId(),
                o.getNode().getId(),
                o.getInheritableKind(),
                o.getOverrideRef(),
                o.getOverrideLabel(),
                o.getEffectiveFrom(),
                o.getEffectiveTo(),
                o.getVersion());
    }
}
