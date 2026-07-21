package com.ewos.onboarding.application;

import com.ewos.onboarding.api.OnboardingMapper;
import com.ewos.onboarding.api.dto.CreateOnboardingTaskTemplateRequest;
import com.ewos.onboarding.api.dto.OnboardingTaskTemplateResponse;
import com.ewos.onboarding.domain.OnboardingTaskOwner;
import com.ewos.onboarding.domain.OnboardingTaskTemplate;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskTemplateRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OnboardingTaskTemplateService {

    private final OnboardingTaskTemplateRepository templates;
    private final OnboardingMapper mapper;

    public OnboardingTaskTemplateService(
            OnboardingTaskTemplateRepository templates, OnboardingMapper mapper) {
        this.templates = templates;
        this.mapper = mapper;
    }

    public OnboardingTaskTemplateResponse create(CreateOnboardingTaskTemplateRequest req) {
        if (templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Onboarding task template code already exists: " + req.code());
        }
        OnboardingTaskTemplate t = new OnboardingTaskTemplate();
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
        t.setDefaultOwner(req.defaultOwner() == null ? OnboardingTaskOwner.HR : req.defaultOwner());
        t.setDefaultSlaDays(req.defaultSlaDays());
        t.setActive(req.active() == null ? true : req.active());
        t = templates.save(t);
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public List<OnboardingTaskTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        return templates
                .findAllByTenantIdAndCompanyIdOrderBySortOrderAsc(tenantId, companyId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        OnboardingTaskTemplate t =
                templates
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Onboarding task template not found"));
        templates.delete(t);
    }

    List<OnboardingTaskTemplate> activeTemplatesFor(UUID tenantId, UUID companyId) {
        return templates.findAllByTenantIdAndCompanyIdAndActiveTrueOrderBySortOrderAsc(
                tenantId, companyId);
    }
}
