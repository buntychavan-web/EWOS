package com.ewos.exit.application;

import com.ewos.exit.api.dto.ChecklistItemResponse;
import com.ewos.exit.api.dto.ChecklistItemSpec;
import com.ewos.exit.api.dto.CreateExitChecklistTemplateRequest;
import com.ewos.exit.api.dto.ExitChecklistTemplateResponse;
import com.ewos.exit.domain.ExitChecklistItemTemplate;
import com.ewos.exit.domain.ExitChecklistTemplate;
import com.ewos.exit.infrastructure.persistence.ExitChecklistItemTemplateRepository;
import com.ewos.exit.infrastructure.persistence.ExitChecklistTemplateRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring + resolution for {@link ExitChecklistTemplate}s (Sprint 26 — configurable exit
 * checklist by company / business unit / department). Business unit and department are both just
 * {@code OrganizationUnit} rows in this codebase, so a single optional {@code orgUnitId} scope
 * covers both; there is no Grade, Designation, or Employee Category master data anywhere in EWOS
 * today; scoping by those is out of Sprint 26's reach and tracked as backlog rather than invented
 * here.
 */
@Service
@Transactional
public class ExitChecklistTemplateService {

    private final ExitChecklistTemplateRepository templates;
    private final ExitChecklistItemTemplateRepository items;
    private final ClientAccessGuard guard;

    public ExitChecklistTemplateService(
            ExitChecklistTemplateRepository templates,
            ExitChecklistItemTemplateRepository items,
            ClientAccessGuard guard) {
        this.templates = templates;
        this.items = items;
        this.guard = guard;
    }

    public ExitChecklistTemplateResponse create(
            UUID tenantId, CreateExitChecklistTemplateRequest req) {
        guard.requireAccessForCompany(req.companyId());
        ExitChecklistTemplate t = new ExitChecklistTemplate();
        t.setTenantId(tenantId);
        t.setCompanyId(req.companyId());
        t.setOrgUnitId(req.orgUnitId());
        t.setName(req.name());
        t.setActive(true);
        t = templates.save(t);

        List<ExitChecklistItemTemplate> saved = new ArrayList<>();
        int sortOrder = 0;
        for (ChecklistItemSpec spec : req.items()) {
            ExitChecklistItemTemplate item = new ExitChecklistItemTemplate();
            item.setTenantId(tenantId);
            item.setTemplate(t);
            item.setDepartment(spec.department());
            item.setItemName(spec.itemName());
            item.setSortOrder(spec.sortOrder() != null ? spec.sortOrder() : sortOrder);
            sortOrder++;
            saved.add(items.save(item));
        }
        return toResponse(t, saved);
    }

    @Transactional(readOnly = true)
    public ExitChecklistTemplateResponse getById(UUID tenantId, UUID id) {
        ExitChecklistTemplate t = require(tenantId, id);
        return toResponse(t, itemsOf(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ExitChecklistTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return templates.findAllByTenantIdAndCompanyIdOrderByNameAsc(tenantId, companyId).stream()
                .map(t -> toResponse(t, itemsOf(tenantId, t.getId())))
                .toList();
    }

    public ExitChecklistTemplateResponse setActive(UUID tenantId, UUID id, boolean active) {
        ExitChecklistTemplate t = require(tenantId, id);
        t.setActive(active);
        return toResponse(t, itemsOf(tenantId, id));
    }

    /**
     * Package-private lookup used by {@link ExitService} to auto-populate the clearance checklist
     * when a resignation is accepted. Deliberately optional (never throws) — a tenant/company/org
     * unit without a configured template falls back to the pre-Sprint-26 manual {@code
     * addClearance} flow.
     */
    @Transactional(readOnly = true)
    Optional<ExitChecklistTemplate> resolveEffective(
            UUID tenantId, UUID companyId, UUID orgUnitId) {
        return templates.findCandidates(tenantId, companyId, orgUnitId).stream().findFirst();
    }

    /** Package-private: items of a resolved template, in checklist order. */
    @Transactional(readOnly = true)
    List<ExitChecklistItemTemplate> itemsOf(UUID tenantId, UUID templateId) {
        return items.findAllByTenantIdAndTemplateIdOrderBySortOrderAsc(tenantId, templateId);
    }

    private ExitChecklistTemplate require(UUID tenantId, UUID id) {
        ExitChecklistTemplate t =
                templates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Exit checklist template not found"));
        guard.requireAccessForCompany(t.getCompanyId());
        return t;
    }

    private ExitChecklistTemplateResponse toResponse(
            ExitChecklistTemplate t, List<ExitChecklistItemTemplate> items) {
        return new ExitChecklistTemplateResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getOrgUnitId(),
                t.getName(),
                t.isActive(),
                items.stream()
                        .map(
                                i ->
                                        new ChecklistItemResponse(
                                                i.getId(),
                                                i.getDepartment(),
                                                i.getItemName(),
                                                i.getSortOrder()))
                        .toList());
    }
}
