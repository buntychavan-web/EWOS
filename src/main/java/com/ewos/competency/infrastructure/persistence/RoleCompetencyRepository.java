package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.RoleCompetency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleCompetencyRepository extends JpaRepository<RoleCompetency, UUID> {

    Optional<RoleCompetency> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RoleCompetency> findAllByTenantIdAndCompanyIdAndDesignationIgnoreCase(
            UUID tenantId, UUID companyId, String designation);

    List<RoleCompetency> findAllByTenantIdAndCompanyIdAndOrgUnitId(
            UUID tenantId, UUID companyId, UUID orgUnitId);
}
