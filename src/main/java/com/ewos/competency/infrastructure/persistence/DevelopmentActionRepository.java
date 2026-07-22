package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.DevelopmentAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevelopmentActionRepository extends JpaRepository<DevelopmentAction, UUID> {

    Optional<DevelopmentAction> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DevelopmentAction> findAllByTenantIdAndPlanId(UUID tenantId, UUID planId);
}
