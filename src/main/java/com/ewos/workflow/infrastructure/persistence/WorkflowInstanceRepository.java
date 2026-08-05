package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowInstance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowInstanceRepository
        extends JpaRepository<WorkflowInstance, UUID>, JpaSpecificationExecutor<WorkflowInstance> {

    Optional<WorkflowInstance> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<WorkflowInstance> findByTenantIdAndCorrelationKey(
            UUID tenantId, String correlationKey);

    List<WorkflowInstance> findAllByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, UUID subjectId);

    /**
     * Serializes concurrent {@code ANY}/{@code ALL} sibling-task completions against the same
     * instance so the "is this the last outstanding task in this state" decision in {@code
     * WorkflowTaskService#complete} is computed against a consistent, committed view of the other
     * sibling rows rather than racing a read against a concurrent transaction's not-yet-committed
     * write (which could otherwise leave every sibling seeing "still waiting" and the instance
     * stalled forever with no tasks actually open).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkflowInstance i where i.id = :id")
    Optional<WorkflowInstance> lockForUpdate(@Param("id") UUID id);
}
