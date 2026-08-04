package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateKnowledgeDocumentRequest;
import com.ewos.payroll.api.dto.KnowledgeDocumentResponse;
import com.ewos.payroll.domain.KnowledgeDocument;
import com.ewos.payroll.domain.KnowledgeDocumentStatus;
import com.ewos.payroll.domain.KnowledgeSourceType;
import com.ewos.payroll.infrastructure.persistence.KnowledgeDocumentRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24K item 7 — Knowledge Centre backend foundation. CRUD + version/publish lifecycle +
 * plain-text search over {@link KnowledgeDocument}. This is the foundation only: no AI retrieval is
 * implemented (see the class javadoc on {@link KnowledgeDocument} and {@code
 * docs/architecture/knowledge-centre-foundation.md}).
 */
@Service
@Transactional
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository repository;
    private final ClientAccessGuard guard;
    private final PayrollMapper mapper;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository repository, ClientAccessGuard guard, PayrollMapper mapper) {
        this.repository = repository;
        this.guard = guard;
        this.mapper = mapper;
    }

    /** Creates a brand-new document family at version 1, status DRAFT. */
    public KnowledgeDocumentResponse create(CreateKnowledgeDocumentRequest request) {
        guardCompany(request.companyId());
        KnowledgeDocument d = new KnowledgeDocument();
        d.setTenantId(request.tenantId());
        d.setCompanyId(request.companyId());
        d.setDocumentFamilyId(UUID.randomUUID());
        d.setVersionNumber(1);
        applyFields(d, request);
        return mapper.toResponse(repository.save(d));
    }

    /**
     * Adds a new DRAFT version to an existing document family — the previous rows are left exactly
     * as they were; nothing is overwritten.
     */
    public KnowledgeDocumentResponse createNewVersion(
            UUID tenantId, UUID documentFamilyId, CreateKnowledgeDocumentRequest request) {
        List<KnowledgeDocument> history =
                repository.findAllByTenantIdAndDocumentFamilyIdOrderByVersionNumberDesc(
                        tenantId, documentFamilyId);
        if (history.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Document family not found");
        }
        guardCompany(request.companyId());
        int nextVersion = history.get(0).getVersionNumber() + 1;
        KnowledgeDocument d = new KnowledgeDocument();
        d.setTenantId(tenantId);
        d.setCompanyId(request.companyId());
        d.setDocumentFamilyId(documentFamilyId);
        d.setVersionNumber(nextVersion);
        applyFields(d, request);
        return mapper.toResponse(repository.save(d));
    }

    /** Publishes a DRAFT version, superseding whichever version was previously PUBLISHED. */
    public KnowledgeDocumentResponse publish(UUID tenantId, UUID id) {
        KnowledgeDocument d = require(tenantId, id);
        guardCompany(d.getCompanyId());
        repository
                .findByTenantIdAndDocumentFamilyIdAndStatus(
                        tenantId, d.getDocumentFamilyId(), KnowledgeDocumentStatus.PUBLISHED)
                .ifPresent(previous -> previous.setStatus(KnowledgeDocumentStatus.SUPERSEDED));
        d.setStatus(KnowledgeDocumentStatus.PUBLISHED);
        return mapper.toResponse(d);
    }

    public KnowledgeDocumentResponse archive(UUID tenantId, UUID id) {
        KnowledgeDocument d = require(tenantId, id);
        guardCompany(d.getCompanyId());
        d.setStatus(KnowledgeDocumentStatus.ARCHIVED);
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> versionHistory(UUID tenantId, UUID documentFamilyId) {
        return repository
                .findAllByTenantIdAndDocumentFamilyIdOrderByVersionNumberDesc(
                        tenantId, documentFamilyId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Every PUBLISHED document effective on {@code asOf}, optionally filtered by source type. */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> effectiveAsOf(
            UUID tenantId, UUID companyId, KnowledgeSourceType sourceType, LocalDate asOf) {
        return repository.findEffectiveAsOf(tenantId, companyId, sourceType, asOf).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Plain substring search over title/summary/tags — the "search" half of this sprint's
     * foundation requirement. Deliberately not semantic/vector search; see this class's javadoc.
     */
    @Transactional(readOnly = true)
    public List<KnowledgeDocumentResponse> search(UUID tenantId, String query) {
        return repository.searchPublished(tenantId, query).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void guardCompany(UUID companyId) {
        if (companyId != null) {
            guard.requireAccessForCompany(companyId);
        }
    }

    private static void applyFields(KnowledgeDocument d, CreateKnowledgeDocumentRequest request) {
        d.setSourceType(request.sourceType());
        d.setTitle(request.title());
        d.setReferenceNumber(request.referenceNumber());
        d.setSummary(request.summary());
        d.setTags(request.tags());
        d.setStorageUri(request.storageUri());
        d.setEffectiveFrom(request.effectiveFrom());
        d.setEffectiveTo(request.effectiveTo());
    }

    private KnowledgeDocument require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Knowledge document not found"));
    }
}
