package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowTask;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowTaskRepository
        extends JpaRepository<WorkflowTask, UUID>, JpaSpecificationExecutor<WorkflowTask> {

    Optional<WorkflowTask> findByIdAndTenantId(UUID id, UUID tenantId);

    List<WorkflowTask> findAllByTenantIdAndAssigneeActorIdAndStatusIn(
            UUID tenantId, UUID assigneeActorId, List<WorkflowTaskStatus> statuses);

    List<WorkflowTask> findAllByTenantIdAndAssigneeRoleCodeAndStatusIn(
            UUID tenantId, String assigneeRoleCode, List<WorkflowTaskStatus> statuses);

    @Query(
            "select t from WorkflowTask t where t.instance.id = :instanceId order by t.createdAt"
                    + " asc")
    List<WorkflowTask> findAllOfInstance(@Param("instanceId") UUID instanceId);

    @Query(
            "select t from WorkflowTask t where t.instance.id = :instanceId and t.status in"
                    + " :statuses")
    List<WorkflowTask> findAllOfInstanceInStatus(
            @Param("instanceId") UUID instanceId,
            @Param("statuses") List<WorkflowTaskStatus> statuses);
}
