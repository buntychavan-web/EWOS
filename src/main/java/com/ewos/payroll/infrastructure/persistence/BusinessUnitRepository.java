package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.BusinessUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessUnitRepository extends JpaRepository<BusinessUnit, UUID> {
    Optional<BusinessUnit> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<BusinessUnit> findAllByTenantIdAndCompanyIdOrderByCodeAsc(UUID tenantId, UUID companyId);
}
