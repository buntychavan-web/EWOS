package com.ewos.organization.domain;

import com.ewos.shared.exception.ApiException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Pure domain policy enforcing organizational hierarchy invariants. Framework-free (Spring
 * annotations aside for wiring): it takes fully-loaded aggregates and answers "is this move
 * allowed?"
 *
 * <p>Invariants enforced:
 *
 * <ul>
 *   <li>Parent must belong to the same tenant AND company as the child.
 *   <li>Parent must not be CLOSED.
 *   <li>Assigning a parent must not create a cycle in the reporting graph.
 *   <li>A unit with active children cannot be closed until all children are closed or re-parented.
 * </ul>
 */
@Component
public final class OrganizationHierarchyPolicy {

    /**
     * @throws ApiException if the proposed parent-child relationship is invalid.
     */
    public void assertValidParent(OrganizationUnit child, OrganizationUnit newParent) {
        if (newParent == null) {
            return;
        }
        if (!newParent.getTenantId().equals(child.getTenantId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Parent unit belongs to a different tenant");
        }
        if (!newParent.getCompanyId().equals(child.getCompanyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Parent unit belongs to a different company");
        }
        if (newParent.getStatus() == OrganizationUnitStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "Parent unit is closed");
        }
        if (child.getId() != null && wouldCreateCycle(child.getId(), newParent)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Assigning this parent would create a hierarchy cycle");
        }
    }

    /**
     * @throws ApiException if closing this unit would orphan children.
     */
    public void assertClosable(OrganizationUnit unit, long activeChildCount) {
        if (activeChildCount > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Unit has "
                            + activeChildCount
                            + " active children; close or re-parent them first");
        }
        if (unit.getStatus() == OrganizationUnitStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "Unit is already closed");
        }
    }

    private boolean wouldCreateCycle(UUID childId, OrganizationUnit candidateParent) {
        Set<UUID> visited = new HashSet<>();
        OrganizationUnit cursor = candidateParent;
        while (cursor != null) {
            if (cursor.getId() != null && cursor.getId().equals(childId)) {
                return true;
            }
            if (cursor.getId() != null && !visited.add(cursor.getId())) {
                return false;
            }
            cursor = cursor.getParent();
        }
        return false;
    }
}
