package com.ewos.organization.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.organization.api.dto.CreateOrganizationNodeRequest;
import com.ewos.organization.api.dto.DeactivateNodeRequest;
import com.ewos.organization.api.dto.MergeNodeRequest;
import com.ewos.organization.api.dto.MoveNodeRequest;
import com.ewos.organization.api.dto.NewNodeSpec;
import com.ewos.organization.api.dto.NodeVersionResponse;
import com.ewos.organization.api.dto.OrganizationNodeResponse;
import com.ewos.organization.api.dto.OrganizationNodeTreeResponse;
import com.ewos.organization.api.dto.RenameNodeRequest;
import com.ewos.organization.api.dto.SplitNodeRequest;
import com.ewos.organization.domain.NodeChangeType;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.domain.OrganizationNodeVersion;
import com.ewos.organization.infrastructure.persistence.OrganizationLevelRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeSpecifications;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeVersionRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core orchestrator for organization nodes. Structural operations (create / rename / move / merge /
 * split / deactivate) always append an {@link OrganizationNodeVersion} row so history is never
 * lost. Employees reference a node id; a move never touches employee rows — the reference is
 * self-updating.
 */
@Service
@Transactional
public class OrganizationNodeService {

    private final OrganizationNodeRepository nodeRepository;
    private final OrganizationLevelRepository levelRepository;
    private final OrganizationNodeVersionRepository versionRepository;
    private final TenantResolver tenantResolver;

    public OrganizationNodeService(
            OrganizationNodeRepository nodeRepository,
            OrganizationLevelRepository levelRepository,
            OrganizationNodeVersionRepository versionRepository,
            TenantResolver tenantResolver) {
        this.nodeRepository = nodeRepository;
        this.levelRepository = levelRepository;
        this.versionRepository = versionRepository;
        this.tenantResolver = tenantResolver;
    }

    // --- CRUD ---------------------------------------------------------------

    public OrganizationNodeResponse create(CreateOrganizationNodeRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        Tenant tenant = tenantResolver.resolve(req.tenantId());

        nodeRepository
                .findByTenantAndCode(tenant, req.code())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "Node code '" + req.code() + "' already exists in tenant");
                        });

        OrganizationLevel level = requireLevel(req.levelId());
        if (!level.getTenant().getId().equals(tenant.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Level belongs to a different tenant");
        }

        OrganizationNode parent = null;
        if (req.parentNodeId() != null) {
            parent = requireNode(req.parentNodeId());
            if (!parent.getTenant().getId().equals(tenant.getId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Parent node belongs to a different tenant");
            }
        }

        OrganizationNode node = new OrganizationNode();
        node.setTenant(tenant);
        node.setLevel(level);
        node.setParent(parent);
        node.setCode(req.code());
        node.setName(req.name());
        node.setEffectiveFrom(req.effectiveFrom());
        node.setActive(true);
        OrganizationNode saved = nodeRepository.save(node);

        writeVersion(
                saved, NodeChangeType.CREATED, req.effectiveFrom(), null, null, "Node created");
        return OrganizationMapper.toNode(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationNodeResponse getById(UUID id) {
        return OrganizationMapper.toNode(requireNode(id));
    }

    @Transactional(readOnly = true)
    public Page<OrganizationNodeResponse> search(
            UUID tenantId,
            UUID levelId,
            UUID parentId,
            Boolean active,
            String search,
            Pageable pageable) {
        return nodeRepository
                .findAll(
                        OrganizationNodeSpecifications.matching(
                                tenantId, levelId, parentId, active, search),
                        pageable)
                .map(OrganizationMapper::toNode);
    }

    @Transactional(readOnly = true)
    public OrganizationNodeTreeResponse tree(UUID rootId) {
        OrganizationNode root = requireNode(rootId);
        return buildTree(root, nodesByParent(root.getTenant()));
    }

    @Transactional(readOnly = true)
    public List<OrganizationNodeTreeResponse> forest(UUID tenantId) {
        Tenant tenant = tenantResolver.resolve(tenantId);
        Map<UUID, List<OrganizationNode>> byParent = nodesByParent(tenant);
        List<OrganizationNode> roots = nodeRepository.findByParentIsNullAndTenant(tenant);
        List<OrganizationNodeTreeResponse> out = new ArrayList<>(roots.size());
        for (OrganizationNode r : roots) {
            out.add(buildTree(r, byParent));
        }
        return out;
    }

    // --- Structural operations ---------------------------------------------

    public OrganizationNodeResponse rename(UUID nodeId, RenameNodeRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        OrganizationNode node = requireNode(nodeId);
        String oldName = node.getName();
        node.setName(req.newName());
        writeVersion(
                node,
                NodeChangeType.RENAMED,
                req.effectiveFrom(),
                null,
                null,
                "Renamed from '"
                        + oldName
                        + "' to '"
                        + req.newName()
                        + "'"
                        + (req.notes() == null ? "" : ": " + req.notes()));
        return OrganizationMapper.toNode(node);
    }

    public OrganizationNodeResponse move(UUID nodeId, MoveNodeRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        OrganizationNode node = requireNode(nodeId);
        OrganizationNode newParent = null;
        if (req.newParentNodeId() != null) {
            newParent = requireNode(req.newParentNodeId());
            if (!newParent.getTenant().getId().equals(node.getTenant().getId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Target parent belongs to a different tenant");
            }
            assertNoCycle(node, newParent);
        }
        node.setParent(newParent);

        writeVersion(
                node,
                NodeChangeType.MOVED,
                req.effectiveFrom(),
                null,
                null,
                "Moved to parent "
                        + (newParent == null ? "<root>" : newParent.getCode())
                        + (req.notes() == null ? "" : ": " + req.notes()));
        return OrganizationMapper.toNode(node);
    }

    public OrganizationNodeResponse mergeInto(UUID sourceId, MergeNodeRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        if (Objects.equals(sourceId, req.targetNodeId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot merge a node into itself");
        }
        OrganizationNode source = requireNode(sourceId);
        OrganizationNode target = requireNode(req.targetNodeId());
        if (!target.getTenant().getId().equals(source.getTenant().getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Target belongs to a different tenant");
        }

        // Move source's children under the target so the tree stays connected.
        List<OrganizationNode> children = nodeRepository.findByParent(source);
        for (OrganizationNode child : children) {
            child.setParent(target);
            writeVersion(
                    child,
                    NodeChangeType.MOVED,
                    req.effectiveFrom(),
                    null,
                    target.getId(),
                    "Auto-moved due to parent merge into " + target.getCode());
        }

        source.setActive(false);
        source.setEffectiveTo(req.effectiveFrom());
        writeVersion(
                source,
                NodeChangeType.MERGED_INTO,
                req.effectiveFrom(),
                req.effectiveFrom(),
                target.getId(),
                "Merged into "
                        + target.getCode()
                        + (req.notes() == null ? "" : ": " + req.notes()));
        writeVersion(
                target,
                NodeChangeType.MERGED_INTO,
                req.effectiveFrom(),
                null,
                source.getId(),
                "Absorbed " + source.getCode() + (req.notes() == null ? "" : ": " + req.notes()));
        return OrganizationMapper.toNode(target);
    }

    public List<OrganizationNodeResponse> split(UUID sourceId, SplitNodeRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        OrganizationNode source = requireNode(sourceId);
        Tenant tenant = source.getTenant();

        List<OrganizationNodeResponse> created = new ArrayList<>();
        for (NewNodeSpec spec : req.newNodes()) {
            nodeRepository
                    .findByTenantAndCode(tenant, spec.code())
                    .ifPresent(
                            existing -> {
                                throw new ApiException(
                                        HttpStatus.CONFLICT,
                                        "Node code '" + spec.code() + "' already exists in tenant");
                            });
            OrganizationNode child = new OrganizationNode();
            child.setTenant(tenant);
            child.setLevel(source.getLevel());
            child.setParent(source.getParent());
            child.setCode(spec.code());
            child.setName(spec.name());
            child.setEffectiveFrom(req.effectiveFrom());
            child.setActive(true);
            OrganizationNode saved = nodeRepository.save(child);
            writeVersion(
                    saved,
                    NodeChangeType.SPLIT_FROM,
                    req.effectiveFrom(),
                    null,
                    source.getId(),
                    "Split from "
                            + source.getCode()
                            + (req.notes() == null ? "" : ": " + req.notes()));
            created.add(OrganizationMapper.toNode(saved));
        }

        if (req.deactivateSource()) {
            source.setActive(false);
            source.setEffectiveTo(req.effectiveFrom());
            writeVersion(
                    source,
                    NodeChangeType.DEACTIVATED,
                    req.effectiveFrom(),
                    req.effectiveFrom(),
                    null,
                    "Deactivated on split");
        }
        return created;
    }

    public OrganizationNodeResponse deactivate(UUID nodeId, DeactivateNodeRequest req) {
        OrganizationNode node = requireNode(nodeId);
        EffectiveDateValidator.requireOrdered(node.getEffectiveFrom(), req.effectiveTo());
        node.setActive(false);
        node.setEffectiveTo(req.effectiveTo());
        writeVersion(
                node,
                NodeChangeType.DEACTIVATED,
                req.effectiveTo(),
                req.effectiveTo(),
                null,
                req.notes());
        return OrganizationMapper.toNode(node);
    }

    public OrganizationNodeResponse reactivate(UUID nodeId, LocalDate effectiveFrom) {
        EffectiveDateValidator.requireOrdered(effectiveFrom, null);
        OrganizationNode node = requireNode(nodeId);
        node.setActive(true);
        node.setEffectiveTo(null);
        writeVersion(node, NodeChangeType.REACTIVATED, effectiveFrom, null, null, "Reactivated");
        return OrganizationMapper.toNode(node);
    }

    public void softDelete(UUID nodeId) {
        OrganizationNode node = requireNode(nodeId);
        if (!nodeRepository.findByParent(node).isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Node has children; move or delete them first");
        }
        nodeRepository.delete(node);
    }

    @Transactional(readOnly = true)
    public List<NodeVersionResponse> versionHistory(UUID nodeId) {
        OrganizationNode node = requireNode(nodeId);
        return versionRepository.findByNodeOrderByEffectiveFromDesc(node).stream()
                .map(OrganizationMapper::toVersion)
                .toList();
    }

    // --- helpers ------------------------------------------------------------

    OrganizationNode requireNode(UUID id) {
        return nodeRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Organization node not found"));
    }

    private OrganizationLevel requireLevel(UUID id) {
        return levelRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Organization level not found"));
    }

    private void assertNoCycle(OrganizationNode node, OrganizationNode candidateParent) {
        OrganizationNode cursor = candidateParent;
        while (cursor != null) {
            if (Objects.equals(cursor.getId(), node.getId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "Move would create a cycle in the organization tree");
            }
            cursor = cursor.getParent();
        }
    }

    private void writeVersion(
            OrganizationNode node,
            NodeChangeType type,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            UUID relatedNodeId,
            String notes) {
        OrganizationNodeVersion v = new OrganizationNodeVersion();
        v.setNode(node);
        v.setChangeType(type);
        v.setEffectiveFrom(effectiveFrom);
        v.setEffectiveTo(effectiveTo);
        v.setSnapshotCode(node.getCode());
        v.setSnapshotName(node.getName());
        v.setSnapshotParentId(node.getParent() == null ? null : node.getParent().getId());
        v.setSnapshotLevelId(node.getLevel().getId());
        v.setRelatedNodeId(relatedNodeId);
        v.setNotes(notes);
        versionRepository.save(v);
    }

    private Map<UUID, List<OrganizationNode>> nodesByParent(Tenant tenant) {
        List<OrganizationNode> all = nodeRepository.findByTenant(tenant);
        Map<UUID, List<OrganizationNode>> byParent = new HashMap<>();
        for (OrganizationNode n : all) {
            UUID key = n.getParent() == null ? null : n.getParent().getId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(n);
        }
        return byParent;
    }

    private OrganizationNodeTreeResponse buildTree(
            OrganizationNode node, Map<UUID, List<OrganizationNode>> byParent) {
        List<OrganizationNode> childList = byParent.getOrDefault(node.getId(), List.of());
        List<OrganizationNodeTreeResponse> children = new ArrayList<>(childList.size());
        for (OrganizationNode c : childList) {
            children.add(buildTree(c, byParent));
        }
        return OrganizationMapper.toTree(node, children);
    }
}
