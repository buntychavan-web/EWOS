package com.ewos.offer.application;

import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.CreateOfferTemplateRequest;
import com.ewos.offer.api.dto.OfferTemplateResponse;
import com.ewos.offer.domain.OfferTemplate;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.domain.events.OfferEventType;
import com.ewos.offer.infrastructure.persistence.OfferTemplateRepository;
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
public class OfferTemplateService {

    private final OfferTemplateRepository templates;
    private final OfferMapper mapper;
    private final ApplicationEventPublisher events;

    public OfferTemplateService(
            OfferTemplateRepository templates,
            OfferMapper mapper,
            ApplicationEventPublisher events) {
        this.templates = templates;
        this.mapper = mapper;
        this.events = events;
    }

    public OfferTemplateResponse create(CreateOfferTemplateRequest req) {
        if (templates.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Offer template code already exists: " + req.code());
        }
        OfferTemplate t = new OfferTemplate();
        t.setTenantId(req.tenantId());
        t.setCompanyId(req.companyId());
        t.setCode(req.code());
        t.setName(req.name());
        t.setDescription(req.description());
        t.setBodyTemplate(req.bodyTemplate());
        t.setDefaultCurrency(req.defaultCurrency());
        t.setDefaultNoticePeriodDays(req.defaultNoticePeriodDays());
        t.setDefaultProbationDays(req.defaultProbationDays());
        if (req.defaultExpiryDays() != null) {
            t.setDefaultExpiryDays(req.defaultExpiryDays());
        }
        t.setActive(req.active() == null ? true : req.active());
        t = templates.save(t);
        publish(OfferEventType.OFFER_TEMPLATE_CREATED, t);
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public OfferTemplateResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<OfferTemplateResponse> listForCompany(UUID tenantId, UUID companyId) {
        return templates.findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        templates.delete(require(tenantId, id));
    }

    OfferTemplate require(UUID tenantId, UUID id) {
        return templates
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Offer template not found"));
    }

    private void publish(OfferEventType type, OfferTemplate t) {
        events.publishEvent(
                new OfferEvent(
                        type,
                        t.getTenantId(),
                        t.getCompanyId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        t.getCode(),
                        OfferSecurity.currentActor(),
                        Instant.now()));
    }
}
