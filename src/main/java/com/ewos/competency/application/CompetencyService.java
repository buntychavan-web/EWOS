package com.ewos.competency.application;

import com.ewos.competency.api.CompetencyMapper;
import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.CreateCompetencyRequest;
import com.ewos.competency.api.dto.UpdateCompetencyRequest;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompetencyService {

    private final CompetencyRepository competencies;
    private final CompetencyMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public CompetencyService(
            CompetencyRepository competencies,
            CompetencyMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.competencies = competencies;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public CompetencyResponse create(CreateCompetencyRequest req) {
        guard.requireAccessForCompany(req.companyId());
        if (req.scaleMax() <= req.scaleMin()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "scale_max must be greater than min");
        }
        if (competencies.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Competency code already exists: " + req.code());
        }
        Competency c = new Competency();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setCategory(req.category());
        c.setScaleMin(req.scaleMin());
        c.setScaleMax(req.scaleMax());
        c.setActive(true);
        c = competencies.save(c);
        publish(CompetencyEventType.COMPETENCY_CREATED, c);
        return mapper.toResponse(c);
    }

    public CompetencyResponse update(UUID tenantId, UUID id, UpdateCompetencyRequest req) {
        Competency c = require(tenantId, id);
        boolean deactivating = c.isActive() && !req.active();
        c.setName(req.name());
        c.setDescription(req.description());
        c.setCategory(req.category());
        c.setScaleMin(req.scaleMin());
        c.setScaleMax(req.scaleMax());
        c.setActive(req.active());
        publish(
                deactivating
                        ? CompetencyEventType.COMPETENCY_DEACTIVATED
                        : CompetencyEventType.COMPETENCY_UPDATED,
                c);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public CompetencyResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<CompetencyResponse> listActive(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return competencies.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Package-private: called only after the caller's own guarded lookup already validated companyId. */
    long activeCount(UUID tenantId, UUID companyId) {
        return competencies.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).size();
    }

    Competency require(UUID tenantId, UUID id) {
        Competency c =
                competencies
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Competency not found"));
        guard.requireAccessForCompany(c.getCompanyId());
        return c;
    }

    void assertLevelInScale(Competency c, int level) {
        if (level < c.getScaleMin() || level > c.getScaleMax()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Level "
                            + level
                            + " outside competency scale ["
                            + c.getScaleMin()
                            + ", "
                            + c.getScaleMax()
                            + "]");
        }
    }

    private void publish(CompetencyEventType type, Competency c) {
        events.publishEvent(
                new CompetencyEvent(
                        type,
                        c.getTenantId(),
                        c.getCompanyId(),
                        c.getId(),
                        null,
                        null,
                        null,
                        null,
                        CompetencySecurity.currentActor(),
                        Instant.now()));
    }
}
