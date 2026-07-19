package com.ewos.employee.infrastructure.persistence;

import com.ewos.employee.domain.EmploymentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentTypeRepository extends JpaRepository<EmploymentType, UUID> {

    Optional<EmploymentType> findByIdAndTenantId(UUID id, UUID tenantId);

    List<EmploymentType> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
