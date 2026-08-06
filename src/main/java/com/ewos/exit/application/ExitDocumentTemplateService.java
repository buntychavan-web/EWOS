package com.ewos.exit.application;

import com.ewos.exit.api.dto.CreateExitDocumentTemplateRequest;
import com.ewos.exit.api.dto.ExitDocumentTemplateResponse;
import com.ewos.exit.domain.ExitDocumentTemplate;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.infrastructure.persistence.ExitDocumentTemplateRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring + resolution for {@link ExitDocumentTemplate}s (Sprint 26 — configurable exit
 * documents). Structurally identical to {@code ExitChecklistTemplateService}'s company/org-unit
 * resolution; see that class for the rationale on why business unit and department share one
 * optional scope field.
 */
@Service
@Transactional
public class ExitDocumentTemplateService {

    private final ExitDocumentTemplateRepository templates;
    private final ClientAccessGuard guard;

    public ExitDocumentTemplateService(
            ExitDocumentTemplateRepository templates, ClientAccessGuard guard) {
        this.templates = templates;
        this.guard = guard;
    }

    public ExitDocumentTemplateResponse create(
            UUID tenantId, CreateExitDocumentTemplateRequest req) {
        guard.requireAccessForCompany(req.companyId());
        ExitDocumentTemplate t = new ExitDocumentTemplate();
        t.setTenantId(tenantId);
        t.setCompanyId(req.companyId());
        t.setOrgUnitId(req.orgUnitId());
        t.setDocumentType(req.documentType());
        t.setTitle(req.title());
        t.setBodyTemplate(req.bodyTemplate());
        t.setActive(true);
        return toResponse(templates.save(t));
    }

    @Transactional(readOnly = true)
    public ExitDocumentTemplateResponse getById(UUID tenantId, UUID id) {
        return toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ExitDocumentTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return templates
                .findAllByTenantIdAndCompanyIdOrderByDocumentTypeAsc(tenantId, companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ExitDocumentTemplateResponse setActive(UUID tenantId, UUID id, boolean active) {
        ExitDocumentTemplate t = require(tenantId, id);
        t.setActive(active);
        return toResponse(t);
    }

    /**
     * Package-private lookup used by {@code ExitDocumentGenerationService}. Deliberately optional —
     * a company/org-unit/document-type without a configured template simply can't be generated yet;
     * the caller surfaces that as "no template configured" rather than falling back to any
     * hard-coded wording.
     */
    @Transactional(readOnly = true)
    Optional<ExitDocumentTemplate> resolveEffective(
            UUID tenantId, UUID companyId, UUID orgUnitId, ExitDocumentType documentType) {
        return templates.findCandidates(tenantId, companyId, orgUnitId, documentType).stream()
                .findFirst();
    }

    private ExitDocumentTemplate require(UUID tenantId, UUID id) {
        ExitDocumentTemplate t =
                templates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Exit document template not found"));
        guard.requireAccessForCompany(t.getCompanyId());
        return t;
    }

    private ExitDocumentTemplateResponse toResponse(ExitDocumentTemplate t) {
        return new ExitDocumentTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getOrgUnitId(),
                t.getDocumentType(),
                t.getTitle(),
                t.getBodyTemplate(),
                t.isActive());
    }
}
