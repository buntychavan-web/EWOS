package com.ewos.workflow.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.CreateDelegationRequest;
import com.ewos.workflow.api.dto.WorkflowDelegationResponse;
import com.ewos.workflow.domain.WorkflowDelegation;
import com.ewos.workflow.infrastructure.persistence.WorkflowDelegationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service: a user delegates their own task inbox to another user for a bounded window. Same
 * shape as the leave/attendance/payroll self-service services elsewhere in the codebase — no
 * {@code @PreAuthorize} beyond authentication, callers act only on their own delegations.
 */
@Service
@Transactional
public class WorkflowDelegationService {

    private final WorkflowDelegationRepository delegations;

    public WorkflowDelegationService(WorkflowDelegationRepository delegations) {
        this.delegations = delegations;
    }

    public WorkflowDelegationResponse create(UUID tenantId, CreateDelegationRequest request) {
        UUID delegator = requireActor();
        if (delegator.equals(request.delegateActorId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot delegate to yourself");
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "endsAt must be after startsAt");
        }
        WorkflowDelegation d = new WorkflowDelegation();
        d.setTenantId(tenantId);
        d.setDelegatorActorId(delegator);
        d.setDelegateActorId(request.delegateActorId());
        d.setRoleCode(request.roleCode());
        d.setStartsAt(request.startsAt());
        d.setEndsAt(request.endsAt());
        d.setActive(true);
        d.setNotes(request.notes());
        return toResponse(delegations.save(d));
    }

    @Transactional(readOnly = true)
    public List<WorkflowDelegationResponse> mine(UUID tenantId) {
        return delegations
                .findAllByTenantIdAndDelegatorActorIdOrderByStartsAtDesc(tenantId, requireActor())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void revoke(UUID tenantId, UUID id) {
        UUID actor = requireActor();
        WorkflowDelegation d =
                delegations
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Delegation not found"));
        if (!d.getDelegatorActorId().equals(actor)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This delegation does not belong to you");
        }
        d.setActive(false);
    }

    /** True if {@code candidateActor} may act on behalf of {@code delegator} right now. */
    @Transactional(readOnly = true)
    public boolean isActiveDelegateOf(UUID tenantId, UUID delegator, UUID candidateActor) {
        return delegations.findActiveFor(tenantId, delegator, Instant.now()).stream()
                .anyMatch(d -> d.getDelegateActorId().equals(candidateActor));
    }

    private WorkflowDelegationResponse toResponse(WorkflowDelegation d) {
        return new WorkflowDelegationResponse(
                d.getId(),
                d.getTenantId(),
                d.getDelegatorActorId(),
                d.getDelegateActorId(),
                d.getRoleCode(),
                d.getStartsAt(),
                d.getEndsAt(),
                d.isActive(),
                d.getNotes(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    private static UUID requireActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user required", e);
        }
    }
}
