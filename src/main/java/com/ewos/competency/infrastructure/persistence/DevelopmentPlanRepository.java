package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.domain.DevelopmentPlanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentPlanRepository extends JpaRepository<DevelopmentPlan, UUID> {

    Optional<DevelopmentPlan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DevelopmentPlan> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<DevelopmentPlan> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, DevelopmentPlanStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, DevelopmentPlanStatus status);
}
