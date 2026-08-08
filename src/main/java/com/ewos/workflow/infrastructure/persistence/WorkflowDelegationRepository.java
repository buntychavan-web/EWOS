package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowDelegation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDelegationRepository extends JpaRepository<WorkflowDelegation, UUID> {

    Optional<WorkflowDelegation> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkflowDelegation> findAllByTenantIdAndDelegatorActorIdOrderByStartsAtDesc(
            UUID tenantId, UUID delegatorActorId);

    @Query(
            "select d from WorkflowDelegation d where d.tenantId = :tenantId and d.delegatorActorId ="
                    + " :delegatorActorId and d.active = true and d.startsAt <= :now and d.endsAt >="
                    + " :now")
    List<WorkflowDelegation> findActiveFor(
            @Param("tenantId") UUID tenantId,
            @Param("delegatorActorId") UUID delegatorActorId,
            @Param("now") Instant now);

    /**
     * Sprint 27B — the inverse of {@link #findActiveFor}: every delegation currently making {@code
     * delegateActorId} someone else's stand-in, powering the approvals inbox's "acting for [X]"
     * peer list.
     */
    @Query(
            "select d from WorkflowDelegation d where d.tenantId = :tenantId and d.delegateActorId ="
                    + " :delegateActorId and d.active = true and d.startsAt <= :now and d.endsAt >="
                    + " :now")
    List<WorkflowDelegation> findActiveDelegatingTo(
            @Param("tenantId") UUID tenantId,
            @Param("delegateActorId") UUID delegateActorId,
            @Param("now") Instant now);
}
