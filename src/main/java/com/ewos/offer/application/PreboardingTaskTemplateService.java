package com.ewos.offer.application;

import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.CreatePreboardingTaskTemplateRequest;
import com.ewos.offer.api.dto.PreboardingTaskTemplateResponse;
import com.ewos.offer.domain.preboarding.PreboardingTaskOwner;
import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import com.ewos.offer.infrastructure.persistence.PreboardingTaskTemplateRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PreboardingTaskTemplateService {

    private final PreboardingTaskTemplateRepository templates;
    private final OfferMapper mapper;

    public PreboardingTaskTemplateService(
            PreboardingTaskTemplateRepository templates, OfferMapper mapper) {
        this.templates = templates;
        this.mapper = mapper;
    }

    public PreboardingTaskTemplateResponse create(CreatePreboardingTaskTemplateRequest req) {
        if (templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Preboarding task template code already exists: " + req.code());
        }
        PreboardingTaskTemplate t = new PreboardingTaskTemplate();
        t.setTenantId(req.tenantId());
        t.setCompanyId(req.companyId());
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setTaskType(req.taskType());
        if (req.sortOrder() != null) {
            t.setSortOrder(req.sortOrder());
        }
        t.setMandatory(req.mandatory() == null ? true : req.mandatory());
        t.setDefaultOwner(
                req.defaultOwner() == null ? PreboardingTaskOwner.HR : req.defaultOwner());
        t.setDefaultSlaDays(req.defaultSlaDays());
        t.setActive(req.active() == null ? true : req.active());
        t = templates.save(t);
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public List<PreboardingTaskTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        return templates
                .findAllByTenantIdAndCompanyIdOrderBySortOrderAsc(tenantId, companyId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        PreboardingTaskTemplate t =
                templates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Preboarding task template not found"));
        templates.delete(t);
    }

    List<PreboardingTaskTemplate> activeTemplatesFor(UUID tenantId, UUID companyId) {
        return templates.findAllByTenantIdAndCompanyIdAndActiveTrueOrderBySortOrderAsc(
                tenantId, companyId);
    }
}
