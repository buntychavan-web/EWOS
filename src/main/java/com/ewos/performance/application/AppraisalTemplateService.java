package com.ewos.performance.application;

import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.AppraisalTemplateResponse;
import com.ewos.performance.api.dto.CreateAppraisalTemplateRequest;
import com.ewos.performance.api.dto.CreateTemplateSectionRequest;
import com.ewos.performance.api.dto.TemplateSectionResponse;
import com.ewos.performance.api.dto.UpdateAppraisalTemplateRequest;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.AppraisalTemplateSection;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalTemplateRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalTemplateSectionRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppraisalTemplateService {

    private final AppraisalTemplateRepository templates;
    private final AppraisalTemplateSectionRepository sections;
    private final PerformanceMapper mapper;
    private final ApplicationEventPublisher events;

    public AppraisalTemplateService(
            AppraisalTemplateRepository templates,
            AppraisalTemplateSectionRepository sections,
            PerformanceMapper mapper,
            ApplicationEventPublisher events) {
        this.templates = templates;
        this.sections = sections;
        this.mapper = mapper;
        this.events = events;
    }

    public AppraisalTemplateResponse create(CreateAppraisalTemplateRequest req) {
        if (req.ratingScaleMax() <= req.ratingScaleMin()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "rating_scale_max must be greater than min");
        }
        if (templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Appraisal template code already exists: " + req.code());
        }
        AppraisalTemplate t = new AppraisalTemplate();
        t.setTenantId(req.tenantId());
        t.setCompanyId(req.companyId());
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setRatingScaleMin(req.ratingScaleMin());
        t.setRatingScaleMax(req.ratingScaleMax());
        t.setActive(true);
        t = templates.save(t);
        publish(PerformanceEventType.TEMPLATE_CREATED, t, null);
        return mapper.toResponse(t);
    }

    public AppraisalTemplateResponse update(
            UUID tenantId, UUID id, UpdateAppraisalTemplateRequest req) {
        AppraisalTemplate t = require(tenantId, id);
        boolean deactivating = t.isActive() && !req.active();
        t.setName(req.name());
        t.setDescription(req.description());
        t.setRatingScaleMin(req.ratingScaleMin());
        t.setRatingScaleMax(req.ratingScaleMax());
        t.setActive(req.active());
        publish(
                deactivating
                        ? PerformanceEventType.TEMPLATE_DEACTIVATED
                        : PerformanceEventType.TEMPLATE_UPDATED,
                t,
                null);
        return mapper.toResponse(t);
    }

    public TemplateSectionResponse addSection(
            UUID tenantId, UUID templateId, CreateTemplateSectionRequest req) {
        AppraisalTemplate t = require(tenantId, templateId);
        AppraisalTemplateSection s = new AppraisalTemplateSection();
        s.setTenantId(tenantId);
        s.setTemplate(t);
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description());
        s.setWeightage(req.weightage());
        s.setDisplayOrder(req.displayOrder());
        s = sections.save(s);
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public AppraisalTemplateResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<AppraisalTemplateResponse> listActive(UUID tenantId, UUID companyId) {
        return templates.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TemplateSectionResponse> listSections(UUID tenantId, UUID templateId) {
        require(tenantId, templateId);
        return sections
                .findAllByTenantIdAndTemplateIdOrderByDisplayOrderAsc(tenantId, templateId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    AppraisalTemplate require(UUID tenantId, UUID id) {
        return templates
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Appraisal template not found"));
    }

    private void publish(PerformanceEventType type, AppraisalTemplate t, String detail) {
        events.publishEvent(
                new PerformanceEvent(
                        type,
                        t.getTenantId(),
                        t.getCompanyId(),
                        null,
                        t.getId(),
                        null,
                        null,
                        null,
                        null,
                        detail,
                        PerformanceSecurity.currentActor(),
                        Instant.now()));
    }
}
