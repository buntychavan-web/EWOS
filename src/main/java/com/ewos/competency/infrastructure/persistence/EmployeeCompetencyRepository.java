package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.EmployeeCompetency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeCompetencyRepository extends JpaRepository<EmployeeCompetency, UUID> {

    Optional<EmployeeCompetency> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<EmployeeCompetency> findByTenantIdAndEmployeeIdAndCompetencyId(
            UUID tenantId, UUID employeeId, UUID competencyId);

    List<EmployeeCompetency> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<EmployeeCompetency> findAllByTenantIdAndCompanyIdAndCompetencyId(
            UUID tenantId, UUID companyId, UUID competencyId);
}
