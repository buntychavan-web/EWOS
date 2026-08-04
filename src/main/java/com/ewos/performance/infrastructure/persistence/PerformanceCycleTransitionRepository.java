package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.PerformanceCycleTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceCycleTransitionRepository
        extends JpaRepository<PerformanceCycleTransition, UUID> {

    List<PerformanceCycleTransition> findAllByTenantIdAndCycleIdOrderByTransitionedAtAsc(
            UUID tenantId, UUID cycleId);
}
