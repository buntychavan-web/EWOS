package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.SuccessorPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuccessorPlanRepository extends JpaRepository<SuccessorPlan, UUID> {

    Optional<SuccessorPlan> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SuccessorPlan> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId);
}
