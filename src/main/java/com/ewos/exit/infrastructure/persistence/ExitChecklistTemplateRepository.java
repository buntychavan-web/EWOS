package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ExitChecklistTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExitChecklistTemplateRepository
        extends JpaRepository<ExitChecklistTemplate, UUID> {

    Optional<ExitChecklistTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExitChecklistTemplate> findAllByTenantIdAndCompanyIdOrderByNameAsc(
            UUID tenantId, UUID companyId);

    /**
     * Active templates that could apply to {@code orgUnitId} — the org-unit-specific template and
     * the company-wide default (org_unit_id null) — ordered so the caller can prefer the most
     * specific match. Same resolution shape as {@code PfConfigurationRepository.findCandidates}.
     */
    @Query(
            "select t from ExitChecklistTemplate t where t.tenantId = :tenantId "
                    + "and t.companyId = :companyId and t.active = true "
                    + "and (t.orgUnitId = :orgUnitId or t.orgUnitId is null) "
                    + "order by t.orgUnitId desc nulls last")
    List<ExitChecklistTemplate> findCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("orgUnitId") UUID orgUnitId);
}
