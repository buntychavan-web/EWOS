package com.ewos.goals.infrastructure.persistence;

import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<Goal> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<Goal> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, GoalStatus status);

    List<Goal> findAllByTenantIdAndCompanyIdAndScope(
            UUID tenantId, UUID companyId, GoalScope scope);

    List<Goal> findAllByTenantIdAndPerformanceCycleId(UUID tenantId, UUID performanceCycleId);

    long countByTenantIdAndCompanyIdAndStatus(UUID tenantId, UUID companyId, GoalStatus status);
}
