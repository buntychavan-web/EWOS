package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select d from WorkflowDefinition d where d.tenantId = :tenantId and lower(d.code) ="
                    + " lower(:code) order by d.definitionVersion desc")
    List<WorkflowDefinition> findByTenantAndCodeAllVersions(
            @Param("tenantId") UUID tenantId, @Param("code") String code);

    @Query(
            "select d from WorkflowDefinition d where d.tenantId = :tenantId and lower(d.code) ="
                    + " lower(:code) and d.active = true order by d.definitionVersion desc")
    List<WorkflowDefinition> findActiveByTenantAndCode(
            @Param("tenantId") UUID tenantId, @Param("code") String code);

    List<WorkflowDefinition> findAllByTenantIdOrderByCodeAscDefinitionVersionDesc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCaseAndDefinitionVersion(
            UUID tenantId, String code, int definitionVersion);
}
