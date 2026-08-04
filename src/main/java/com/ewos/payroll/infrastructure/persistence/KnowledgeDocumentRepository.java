package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.KnowledgeDocument;
import com.ewos.payroll.domain.KnowledgeDocumentStatus;
import com.ewos.payroll.domain.KnowledgeSourceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    Optional<KnowledgeDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    List<KnowledgeDocument> findAllByTenantIdAndDocumentFamilyIdOrderByVersionNumberDesc(
            UUID tenantId, UUID documentFamilyId);

    Optional<KnowledgeDocument> findByTenantIdAndDocumentFamilyIdAndStatus(
            UUID tenantId, UUID documentFamilyId, KnowledgeDocumentStatus status);

    @Query(
            "select d from KnowledgeDocument d where d.tenantId = :tenantId "
                    + "and (:sourceType is null or d.sourceType = :sourceType) "
                    + "and d.status = 'PUBLISHED' "
                    + "and d.effectiveFrom <= :asOf "
                    + "and (d.effectiveTo is null or d.effectiveTo >= :asOf) "
                    + "and (:companyId is null or d.companyId is null or d.companyId = :companyId) "
                    + "order by d.effectiveFrom desc")
    List<KnowledgeDocument> findEffectiveAsOf(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("sourceType") KnowledgeSourceType sourceType,
            @Param("asOf") LocalDate asOf);

    @Query(
            "select d from KnowledgeDocument d where d.tenantId = :tenantId "
                    + "and d.status = 'PUBLISHED' "
                    + "and (lower(d.title) like lower(concat('%', :query, '%')) "
                    + "or lower(d.summary) like lower(concat('%', :query, '%')) "
                    + "or lower(d.tags) like lower(concat('%', :query, '%'))) "
                    + "order by d.effectiveFrom desc")
    List<KnowledgeDocument> searchPublished(
            @Param("tenantId") UUID tenantId, @Param("query") String query);
}
