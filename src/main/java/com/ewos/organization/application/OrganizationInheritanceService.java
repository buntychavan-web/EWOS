package com.ewos.organization.application;

import com.ewos.common.exception.ApiException;
import com.ewos.organization.api.dto.InheritanceOverrideResponse;
import com.ewos.organization.api.dto.ResolvedInheritanceResponse;
import com.ewos.organization.api.dto.SetInheritanceOverrideRequest;
import com.ewos.organization.domain.InheritableKind;
import com.ewos.organization.domain.OrganizationInheritanceOverride;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.infrastructure.persistence.OrganizationInheritanceOverrideRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inheritance resolution and override management.
 *
 * <p>Resolution algorithm for a given (node, kind, asOf):
 *
 * <ol>
 *   <li>Look for a live override on the node itself.
 *   <li>Walk up the parent chain, returning the first live override found.
 *   <li>If nothing in the chain has an override, return an unresolved result. The caller (or a
 *       future consumer) is expected to fall through to the company-level assignment from Sprint 6.
 * </ol>
 *
 * <p>"Live" means {@code effective_from <= asOf} and ({@code effective_to} is null or {@code >=
 * asOf}).
 */
@Service
@Transactional
public class OrganizationInheritanceService {

    private final OrganizationInheritanceOverrideRepository overrideRepository;
    private final OrganizationNodeRepository nodeRepository;

    public OrganizationInheritanceService(
            OrganizationInheritanceOverrideRepository overrideRepository,
            OrganizationNodeRepository nodeRepository) {
        this.overrideRepository = overrideRepository;
        this.nodeRepository = nodeRepository;
    }

    public InheritanceOverrideResponse setOverride(UUID nodeId, SetInheritanceOverrideRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), req.effectiveTo());
        OrganizationNode node = requireNode(nodeId);
        assertNoOverlap(node, req.inheritableKind(), req.effectiveFrom(), req.effectiveTo());

        OrganizationInheritanceOverride o = new OrganizationInheritanceOverride();
        o.setNode(node);
        o.setInheritableKind(req.inheritableKind());
        o.setOverrideRef(req.overrideRef());
        o.setOverrideLabel(req.overrideLabel());
        o.setEffectiveFrom(req.effectiveFrom());
        o.setEffectiveTo(req.effectiveTo());
        return OrganizationMapper.toOverride(overrideRepository.save(o));
    }

    public InheritanceOverrideResponse retireOverride(
            UUID nodeId, UUID overrideId, LocalDate effectiveTo) {
        OrganizationInheritanceOverride o =
                overrideRepository
                        .findById(overrideId)
                        .filter(x -> x.getNode().getId().equals(nodeId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Inheritance override not found for node"));
        EffectiveDateValidator.requireOrdered(o.getEffectiveFrom(), effectiveTo);
        o.setEffectiveTo(effectiveTo);
        return OrganizationMapper.toOverride(o);
    }

    @Transactional(readOnly = true)
    public List<InheritanceOverrideResponse> list(UUID nodeId, InheritableKind kind) {
        OrganizationNode node = requireNode(nodeId);
        List<OrganizationInheritanceOverride> rows =
                kind == null
                        ? overrideRepository.findByNode(node)
                        : overrideRepository.findByNodeAndInheritableKind(node, kind);
        return rows.stream().map(OrganizationMapper::toOverride).toList();
    }

    @Transactional(readOnly = true)
    public ResolvedInheritanceResponse resolve(UUID nodeId, InheritableKind kind, LocalDate asOf) {
        LocalDate at = asOf == null ? LocalDate.now() : asOf;
        OrganizationNode cursor = requireNode(nodeId);
        while (cursor != null) {
            OrganizationInheritanceOverride live = liveOverride(cursor, kind, at);
            if (live != null) {
                return new ResolvedInheritanceResponse(
                        kind, cursor.getId(), live.getOverrideRef(), live.getOverrideLabel(), true);
            }
            cursor = cursor.getParent();
        }
        return new ResolvedInheritanceResponse(kind, null, null, null, false);
    }

    private OrganizationInheritanceOverride liveOverride(
            OrganizationNode node, InheritableKind kind, LocalDate at) {
        List<OrganizationInheritanceOverride> rows =
                overrideRepository.findByNodeAndInheritableKind(node, kind);
        for (OrganizationInheritanceOverride o : rows) {
            LocalDate from = o.getEffectiveFrom();
            LocalDate to = o.getEffectiveTo();
            boolean live = !from.isAfter(at) && (to == null || !to.isBefore(at));
            if (live) {
                return o;
            }
        }
        return null;
    }

    private void assertNoOverlap(
            OrganizationNode node, InheritableKind kind, LocalDate from, LocalDate to) {
        List<OrganizationInheritanceOverride> existing =
                overrideRepository.findByNodeAndInheritableKind(node, kind);
        LocalDate newTo = to == null ? LocalDate.MAX : to;
        for (OrganizationInheritanceOverride other : existing) {
            LocalDate otherFrom = other.getEffectiveFrom();
            LocalDate otherTo =
                    other.getEffectiveTo() == null ? LocalDate.MAX : other.getEffectiveTo();
            boolean overlaps = !newTo.isBefore(otherFrom) && !from.isAfter(otherTo);
            if (overlaps) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "Inheritance override for "
                                + kind
                                + " overlaps window ["
                                + otherFrom
                                + ", "
                                + (other.getEffectiveTo() == null ? "open" : otherTo)
                                + "]");
            }
        }
    }

    private OrganizationNode requireNode(UUID id) {
        return nodeRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Organization node not found"));
    }
}
