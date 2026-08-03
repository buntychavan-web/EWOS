package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.LtaBlockConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LtaBlockConfigurationRepository
        extends JpaRepository<LtaBlockConfiguration, UUID> {

    Optional<LtaBlockConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Candidates for the effective configuration of a company: a company-specific row if one
     * exists, else the tenant-wide row ({@code companyId IS NULL}). Ordered so the caller can just
     * take the first result — a company-specific override always wins over the tenant default.
     */
    @Query(
            "select c from LtaBlockConfiguration c where c.tenantId = :tenantId "
                    + "and (c.companyId = :companyId or c.companyId is null) and c.active = true "
                    + "order by c.companyId nulls last")
    List<LtaBlockConfiguration> findActiveCandidates(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

    List<LtaBlockConfiguration> findAllByTenantIdOrderByEffectiveFromDesc(UUID tenantId);
}
