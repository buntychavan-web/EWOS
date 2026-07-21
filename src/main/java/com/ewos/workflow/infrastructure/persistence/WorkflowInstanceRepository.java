package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WorkflowInstanceRepository
        extends JpaRepository<WorkflowInstance, UUID>, JpaSpecificationExecutor<WorkflowInstance> {

    Optional<WorkflowInstance> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<WorkflowInstance> findByTenantIdAndCorrelationKey(
            UUID tenantId, String correlationKey);

    List<WorkflowInstance> findAllByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, UUID subjectId);
}
