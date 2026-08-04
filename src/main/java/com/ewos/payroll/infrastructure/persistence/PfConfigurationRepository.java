package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PfConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PfConfigurationRepository extends JpaRepository<PfConfiguration, UUID> {

    Optional<PfConfiguration> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Every active row that could apply to {@code companyId} — company-specific rows and the
     * tenant-wide default (company_id null) — ordered so the caller's effective-date resolution can
     * prefer the most specific, most recently effective row.
     */
    @Query(
            "select c from PfConfiguration c where c.tenantId = :tenantId and c.active = true "
                    + "and (c.companyId = :companyId or c.companyId is null) "
                    + "order by c.companyId desc nulls last, c.effectiveFrom desc")
    List<PfConfiguration> findCandidates(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);
}
