package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.CostCentre;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostCentreRepository extends JpaRepository<CostCentre, UUID> {
    Optional<CostCentre> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<CostCentre> findAllByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);
}
