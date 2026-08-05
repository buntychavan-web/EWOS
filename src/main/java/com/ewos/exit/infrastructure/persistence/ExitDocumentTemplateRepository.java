package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.ExitDocumentTemplate;
import com.ewos.exit.domain.ExitDocumentType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExitDocumentTemplateRepository extends JpaRepository<ExitDocumentTemplate, UUID> {

    Optional<ExitDocumentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ExitDocumentTemplate> findAllByTenantIdAndCompanyIdOrderByDocumentTypeAsc(
            UUID tenantId, UUID companyId);

    /**
     * Active templates for a document type that could apply to {@code orgUnitId} — same
     * most-specific-wins resolution shape as {@code
     * ExitChecklistTemplateRepository.findCandidates}.
     */
    @Query(
            "select t from ExitDocumentTemplate t where t.tenantId = :tenantId "
                    + "and t.companyId = :companyId and t.documentType = :documentType "
                    + "and t.active = true and (t.orgUnitId = :orgUnitId or t.orgUnitId is null) "
                    + "order by t.orgUnitId desc nulls last")
    List<ExitDocumentTemplate> findCandidates(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("orgUnitId") UUID orgUnitId,
            @Param("documentType") ExitDocumentType documentType);
}
