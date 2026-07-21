package com.ewos.interview.application;

import com.ewos.interview.api.InterviewMapper;
import com.ewos.interview.api.dto.CreateInterviewTemplateRequest;
import com.ewos.interview.api.dto.InterviewTemplateResponse;
import com.ewos.interview.api.dto.UpdateInterviewTemplateRequest;
import com.ewos.interview.domain.InterviewTemplate;
import com.ewos.interview.domain.events.InterviewEvent;
import com.ewos.interview.domain.events.InterviewEventType;
import com.ewos.interview.infrastructure.persistence.InterviewTemplateRepository;
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
public class InterviewTemplateService {

    private final InterviewTemplateRepository templates;
    private final InterviewMapper mapper;
    private final ApplicationEventPublisher events;

    public InterviewTemplateService(
            InterviewTemplateRepository templates,
            InterviewMapper mapper,
            ApplicationEventPublisher events) {
        this.templates = templates;
        this.mapper = mapper;
        this.events = events;
    }

    public InterviewTemplateResponse create(CreateInterviewTemplateRequest req) {
        if (templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Interview template code already exists: " + req.code());
        }
        InterviewTemplate t = new InterviewTemplate();
        t.setTenantId(req.tenantId());
        t.setCompanyId(req.companyId());
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setInterviewType(req.interviewType());
        if (req.defaultDurationMinutes() != null) {
            t.setDefaultDurationMinutes(req.defaultDurationMinutes());
        }
        t.setScorecardSchema(req.scorecardSchema());
        t.setActive(req.active() == null ? true : req.active());
        t = templates.save(t);
        publish(InterviewEventType.TEMPLATE_CREATED, t);
        return mapper.toResponse(t);
    }

    public InterviewTemplateResponse update(
            UUID tenantId, UUID id, UpdateInterviewTemplateRequest req) {
        InterviewTemplate t = require(tenantId, id);
        boolean wasActive = t.isActive();
        t.setName(req.name());
        t.setDescription(req.description());
        t.setInterviewType(req.interviewType());
        if (req.defaultDurationMinutes() != null) {
            t.setDefaultDurationMinutes(req.defaultDurationMinutes());
        }
        t.setScorecardSchema(req.scorecardSchema());
        if (req.active() != null) {
            t.setActive(req.active());
        }
        publish(InterviewEventType.TEMPLATE_UPDATED, t);
        if (wasActive != t.isActive()) {
            publish(
                    t.isActive()
                            ? InterviewEventType.TEMPLATE_ACTIVATED
                            : InterviewEventType.TEMPLATE_DEACTIVATED,
                    t);
        }
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public InterviewTemplateResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<InterviewTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        return templates.findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        templates.delete(require(tenantId, id));
    }

    InterviewTemplate require(UUID tenantId, UUID id) {
        return templates
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Interview template not found"));
    }

    private void publish(InterviewEventType type, InterviewTemplate t) {
        events.publishEvent(
                new InterviewEvent(
                        type,
                        t.getTenantId(),
                        t.getCompanyId(),
                        null,
                        null,
                        t.getId(),
                        null,
                        t.getCode(),
                        InterviewSecurity.currentActor(),
                        Instant.now()));
    }
}
