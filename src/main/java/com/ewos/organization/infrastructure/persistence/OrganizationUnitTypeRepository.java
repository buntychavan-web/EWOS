package com.ewos.organization.infrastructure.persistence;

import com.ewos.organization.domain.OrganizationUnitType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationUnitTypeRepository extends JpaRepository<OrganizationUnitType, UUID> {

    Optional<OrganizationUnitType> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select t from OrganizationUnitType t where t.tenantId = :tenantId and lower(t.code) ="
                    + " lower(:code)")
    Optional<OrganizationUnitType> findByTenantAndCodeIgnoreCase(
            @Param("tenantId") UUID tenantId, @Param("code") String code);

    List<OrganizationUnitType> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
