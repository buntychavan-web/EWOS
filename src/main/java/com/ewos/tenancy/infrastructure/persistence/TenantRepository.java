package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.Tenant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code @SQLRestriction("deleted_at IS NULL")} on {@link Tenant} already filters every query
 * (including the inherited {@code findById}), so no explicit "and not deleted" methods are needed
 * here — matching the existing convention (see {@code OrganizationUnitTypeRepository}).
 */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<Tenant> findAllByOrderByNameAsc();
}
