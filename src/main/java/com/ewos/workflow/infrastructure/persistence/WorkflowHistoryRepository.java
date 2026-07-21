package com.ewos.workflow.infrastructure.persistence;

import com.ewos.workflow.domain.WorkflowHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, UUID> {

    @Query(
            "select h from WorkflowHistory h where h.instance.id = :instanceId order by"
                    + " h.occurredAt asc")
    List<WorkflowHistory> findAllOfInstance(@Param("instanceId") UUID instanceId);
}
