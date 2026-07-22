package com.ewos.goals.infrastructure.persistence;

import com.ewos.goals.domain.GoalProgressUpdate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalProgressUpdateRepository extends JpaRepository<GoalProgressUpdate, UUID> {

    List<GoalProgressUpdate> findAllByTenantIdAndGoalIdOrderByRecordedAtDesc(
            UUID tenantId, UUID goalId);
}
