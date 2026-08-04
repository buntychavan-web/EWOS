package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.GratuityConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GratuityConfigurationRepository
        extends JpaRepository<GratuityConfiguration, UUID> {

    Optional<GratuityConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select c from GratuityConfiguration c where c.tenantId = :tenantId and c.active = true "
                    + "and (c.companyId = :companyId or c.companyId is null) "
                    + "order by c.companyId desc nulls last, c.effectiveFrom desc")
    List<GratuityConfiguration> findCandidates(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);
}
