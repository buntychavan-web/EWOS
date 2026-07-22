package com.ewos.competency.application;

import com.ewos.competency.api.CompetencyMapper;
import com.ewos.competency.api.dto.RoleCompetencyRequest;
import com.ewos.competency.api.dto.RoleCompetencyResponse;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.RoleCompetency;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.RoleCompetencyRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleCompetencyService {

    private final RoleCompetencyRepository roleCompetencies;
    private final CompetencyService competencies;
    private final CompetencyMapper mapper;
    private final ApplicationEventPublisher events;

    public RoleCompetencyService(
            RoleCompetencyRepository roleCompetencies,
            CompetencyService competencies,
            CompetencyMapper mapper,
            ApplicationEventPublisher events) {
        this.roleCompetencies = roleCompetencies;
        this.competencies = competencies;
        this.mapper = mapper;
        this.events = events;
    }

    public RoleCompetencyResponse set(RoleCompetencyRequest req) {
        Competency c = competencies.require(req.tenantId(), req.competencyId());
        competencies.assertLevelInScale(c, req.requiredLevel());
        RoleCompetency r = new RoleCompetency();
        r.setTenantId(req.tenantId());
        r.setCompanyId(req.companyId());
        r.setOrgUnitId(req.orgUnitId());
        r.setDesignation(req.designation());
        r.setCompetency(c);
        r.setRequiredLevel(req.requiredLevel());
        r.setWeightage(req.weightage());
        r.setNotes(req.notes());
        r = roleCompetencies.save(r);
        publish(CompetencyEventType.ROLE_COMPETENCY_SET, c);
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<RoleCompetencyResponse> forDesignation(
            UUID tenantId, UUID companyId, String designation) {
        return roleCompetencies
                .findAllByTenantIdAndCompanyIdAndDesignationIgnoreCase(
                        tenantId, companyId, designation)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleCompetencyResponse> forOrgUnit(UUID tenantId, UUID companyId, UUID orgUnitId) {
        return roleCompetencies
                .findAllByTenantIdAndCompanyIdAndOrgUnitId(tenantId, companyId, orgUnitId)
                .stream()
                .map(mapper::toResponse)
                .toList();
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
