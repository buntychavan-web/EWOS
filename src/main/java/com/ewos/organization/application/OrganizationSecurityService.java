package com.ewos.organization.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.organization.domain.OrganizationNode;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hierarchy-based authorization helper. Given a set of node ids a caller "manages" (typically
 * populated from their assignments in a future Employee module), returns the transitive closure of
 * those nodes plus every descendant. A manager configured for a parent therefore automatically
 * gains visibility into every child node — moves propagate for free because the reference chain
 * moves with the node.
 */
@Service
@Transactional(readOnly = true)
public class OrganizationSecurityService {

    private final OrganizationNodeRepository nodeRepository;

    public OrganizationSecurityService(OrganizationNodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    /**
     * Expand the given root ids to include every descendant in the same tenant. If {@code rootIds}
     * is empty the caller sees nothing — respect the tenant's data-isolation policy by not silently
     * opening the whole tree.
     */
    public Set<UUID> accessibleNodeIds(UUID tenantId, Set<UUID> rootIds) {
        Set<UUID> out = new HashSet<>();
        if (rootIds == null || rootIds.isEmpty()) {
            return out;
        }
        Tenant tenant = null;
        Map<UUID, List<OrganizationNode>> byParent = new HashMap<>();
        for (OrganizationNode n : nodeRepository.findAll()) {
            if (tenantId != null && !n.getTenant().getId().equals(tenantId)) {
                continue;
            }
            if (tenant == null) {
                tenant = n.getTenant();
            }
            UUID key = n.getParent() == null ? null : n.getParent().getId();
            byParent.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(n);
        }

        Deque<UUID> queue = new ArrayDeque<>(rootIds);
        while (!queue.isEmpty()) {
            UUID cur = queue.pop();
            if (!out.add(cur)) {
                continue;
            }
            for (OrganizationNode child : byParent.getOrDefault(cur, List.of())) {
                queue.push(child.getId());
            }
        }
        return out;
    }

    /**
     * Assert the caller can operate on the given node. Throws {@code 403} if {@code nodeId} is not
     * in the caller's accessible set.
     */
    public void requireAccess(UUID tenantId, Set<UUID> rootIds, UUID nodeId) {
        if (!accessibleNodeIds(tenantId, rootIds).contains(nodeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Node is outside the caller's scope");
        }
    }
}
