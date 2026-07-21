package com.ewos.recruitment.application;

import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.api.RecruitmentMapper;
import com.ewos.recruitment.api.dto.CreateJobPositionRequest;
import com.ewos.recruitment.api.dto.JobPositionResponse;
import com.ewos.recruitment.api.dto.UpdateJobPositionRequest;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.events.RecruitmentEvent;
import com.ewos.recruitment.domain.events.RecruitmentEventType;
import com.ewos.recruitment.infrastructure.persistence.JobPositionRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class JobPositionService {

    private final JobPositionRepository positions;
    private final OrganizationUnitRepository orgUnits;
    private final RecruitmentMapper mapper;
    private final ApplicationEventPublisher events;

    public JobPositionService(
            JobPositionRepository positions,
            OrganizationUnitRepository orgUnits,
            RecruitmentMapper mapper,
            ApplicationEventPublisher events) {
        this.positions = positions;
        this.orgUnits = orgUnits;
        this.mapper = mapper;
        this.events = events;
    }

    public JobPositionResponse create(CreateJobPositionRequest req) {
        if (positions.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Job position code already exists for this company: " + req.code());
        }
        assertSalaryBand(req.salaryMin(), req.salaryMax());

        JobPosition p = new JobPosition();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setCode(req.code());
        p.setTitle(req.title());
        p.setDescription(req.description());
        p.setDepartmentOrgUnit(resolveOrgUnit(req.tenantId(), req.departmentOrgUnitId()));
        p.setLocation(req.location());
        p.setEmploymentType(req.employmentType());
        p.setGrade(req.grade());
        p.setSalaryCurrency(req.salaryCurrency());
        p.setSalaryMin(req.salaryMin());
        p.setSalaryMax(req.salaryMax());
        p.setActive(req.active() == null ? true : req.active());
        p = positions.save(p);

        publish(RecruitmentEventType.POSITION_CREATED, p);
        return mapper.toResponse(p);
    }

    public JobPositionResponse update(UUID tenantId, UUID id, UpdateJobPositionRequest req) {
        JobPosition p = require(tenantId, id);
        assertSalaryBand(req.salaryMin(), req.salaryMax());

        boolean wasActive = p.isActive();
        p.setTitle(req.title());
        p.setDescription(req.description());
        p.setDepartmentOrgUnit(resolveOrgUnit(tenantId, req.departmentOrgUnitId()));
        p.setLocation(req.location());
        p.setEmploymentType(req.employmentType());
        p.setGrade(req.grade());
        p.setSalaryCurrency(req.salaryCurrency());
        p.setSalaryMin(req.salaryMin());
        p.setSalaryMax(req.salaryMax());
        if (req.active() != null) {
            p.setActive(req.active());
        }

        publish(RecruitmentEventType.POSITION_UPDATED, p);
        if (wasActive != p.isActive()) {
            publish(
                    p.isActive()
                            ? RecruitmentEventType.POSITION_ACTIVATED
                            : RecruitmentEventType.POSITION_DEACTIVATED,
                    p);
        }
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public JobPositionResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<JobPositionResponse> listForCompany(UUID tenantId, UUID companyId) {
        return positions.findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        JobPosition p = require(tenantId, id);
        positions.delete(p);
    }

    private JobPosition require(UUID tenantId, UUID id) {
        return positions
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Job position not found"));
    }

    private OrganizationUnit resolveOrgUnit(UUID tenantId, UUID orgUnitId) {
        if (orgUnitId == null) {
            return null;
        }
        return orgUnits.findByIdAndTenantId(orgUnitId, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.BAD_REQUEST, "Department org unit not found"));
    }

    private void assertSalaryBand(java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Salary max must be greater than or equal to min");
        }
    }

    private void publish(RecruitmentEventType type, JobPosition p) {
        events.publishEvent(
                new RecruitmentEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        p.getId(),
                        null,
                        null,
                        null,
                        currentActor(),
                        Instant.now()));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
