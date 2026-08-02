package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.LwfConfiguration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LwfConfigurationRepository extends JpaRepository<LwfConfiguration, UUID> {

    java.util.Optional<LwfConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select c from LwfConfiguration c where c.tenantId = :tenantId "
                    + "and c.jurisdiction.id = :jurisdictionId and c.active = true "
                    + "order by c.effectiveFrom desc")
    List<LwfConfiguration> findActiveForJurisdiction(
            @Param("tenantId") UUID tenantId, @Param("jurisdictionId") UUID jurisdictionId);
}
