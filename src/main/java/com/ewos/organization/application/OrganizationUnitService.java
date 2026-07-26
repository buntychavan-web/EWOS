package com.ewos.organization.application;

import com.ewos.organization.api.OrganizationMapper;
import com.ewos.organization.api.dto.CreateOrganizationUnitRequest;
import com.ewos.organization.api.dto.OrganizationUnitResponse;
import com.ewos.organization.api.dto.OrganizationUnitSearchCriteria;
import com.ewos.organization.api.dto.OrganizationUnitTreeNode;
import com.ewos.organization.api.dto.UpdateOrganizationUnitRequest;
import com.ewos.organization.domain.OrganizationHierarchyPolicy;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.domain.OrganizationUnitStatus;
import com.ewos.organization.domain.OrganizationUnitType;
import com.ewos.organization.domain.events.OrganizationUnitEvent;
import com.ewos.organization.domain.events.OrganizationUnitEventType;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitSpecifications;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitTypeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationUnitService {

    private final OrganizationUnitRepository unitRepository;
    private final OrganizationUnitTypeRepository unitTypeRepository;
    private final OrganizationHierarchyPolicy hierarchyPolicy;
    private final OrganizationMapper mapper;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public OrganizationUnitService(
            OrganizationUnitRepository unitRepository,
            OrganizationUnitTypeRepository unitTypeRepository,
            OrganizationHierarchyPolicy hierarchyPolicy,
            OrganizationMapper mapper,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.unitRepository = unitRepository;
        this.unitTypeRepository = unitTypeRepository;
        this.hierarchyPolicy = hierarchyPolicy;
        this.mapper = mapper;
        this.events = events;
        this.guard = guard;
    }

    public OrganizationUnitResponse create(CreateOrganizationUnitRequest request) {
        guard.requireAccessForCompany(request.companyId());
        if (unitRepository.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                request.tenantId(), request.companyId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Unit code already in use for this tenant and company");
        }
        OrganizationUnitType type = requireType(request.tenantId(), request.unitTypeId());
        OrganizationUnit unit = new OrganizationUnit();
        unit.setTenantId(request.tenantId());
        unit.setCompanyId(request.companyId());
        unit.setUnitType(type);
        unit.setCode(request.code());
        unit.setName(request.name());
        unit.setDescription(request.description());
        unit.setCountryCode(normaliseCountry(request.countryCode()));
        unit.setCostCenterCode(request.costCenterCode());
        unit.setManagerPersonId(request.managerPersonId());
        unit.setStatus(OrganizationUnitStatus.ACTIVE);
        unit.setEffectiveFrom(request.effectiveFrom());
        unit.setEffectiveTo(request.effectiveTo());

        if (request.parentId() != null) {
            OrganizationUnit parent = requireUnit(request.tenantId(), request.parentId());
            unit.setParent(parent);
            hierarchyPolicy.assertValidParent(unit, parent);
        }

        OrganizationUnit saved = unitRepository.save(unit);
        publish(OrganizationUnitEventType.CREATED, saved);
        return mapper.toResponse(saved);
    }

    public OrganizationUnitResponse update(
            UUID tenantId, UUID id, UpdateOrganizationUnitRequest request) {
        OrganizationUnit existing = requireUnit(tenantId, id);
        guard.requireAccessForCompany(existing.getCompanyId());
        boolean reparented = false;

        if (request.unitTypeId() != null
                && (existing.getUnitType() == null
                        || !existing.getUnitType().getId().equals(request.unitTypeId()))) {
            existing.setUnitType(requireType(tenantId, request.unitTypeId()));
        }
        if (request.name() != null) {
            existing.setName(request.name());
        }
        if (request.description() != null) {
            existing.setDescription(request.description());
        }
        if (request.countryCode() != null) {
            existing.setCountryCode(normaliseCountry(request.countryCode()));
        }
        if (request.costCenterCode() != null) {
            existing.setCostCenterCode(request.costCenterCode());
        }
        if (request.managerPersonId() != null) {
            existing.setManagerPersonId(request.managerPersonId());
        }
        if (request.effectiveTo() != null) {
            LocalDate to = request.effectiveTo();
            if (to.isBefore(existing.getEffectiveFrom())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "effectiveTo must be on or after effectiveFrom");
            }
            existing.setEffectiveTo(to);
        }
        if (request.parentId() != null
                && (existing.getParent() == null
                        || !existing.getParent().getId().equals(request.parentId()))) {
            OrganizationUnit newParent = requireUnit(tenantId, request.parentId());
            hierarchyPolicy.assertValidParent(existing, newParent);
            existing.setParent(newParent);
            reparented = true;
        }
        publish(
                reparented
                        ? OrganizationUnitEventType.REPARENTED
                        : OrganizationUnitEventType.UPDATED,
                existing);
        return mapper.toResponse(existing);
    }

    public OrganizationUnitResponse changeStatus(
            UUID tenantId, UUID id, OrganizationUnitStatus target) {
        OrganizationUnit unit = requireUnit(tenantId, id);
        guard.requireAccessForCompany(unit.getCompanyId());
        Objects.requireNonNull(target, "target status");
        if (unit.getStatus() == target) {
            return mapper.toResponse(unit);
        }
        if (target == OrganizationUnitStatus.CLOSED) {
            long activeKids = unitRepository.countNonClosedChildren(id);
            hierarchyPolicy.assertClosable(unit, activeKids);
            unit.setEffectiveTo(LocalDate.now());
        }
        unit.setStatus(target);
        OrganizationUnitEventType eventType =
                target == OrganizationUnitStatus.CLOSED
                        ? OrganizationUnitEventType.CLOSED
                        : OrganizationUnitEventType.STATUS_CHANGED;
        publish(eventType, unit);
        return mapper.toResponse(unit);
    }

    public void delete(UUID tenantId, UUID id) {
        OrganizationUnit unit = requireUnit(tenantId, id);
        guard.requireAccessForCompany(unit.getCompanyId());
        long activeKids = unitRepository.countChildren(id);
        if (activeKids > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Unit has " + activeKids + " children; delete or re-parent them first");
        }
        unitRepository.delete(unit);
        publish(OrganizationUnitEventType.DELETED, unit);
    }

    @Transactional(readOnly = true)
    public OrganizationUnitResponse getById(UUID tenantId, UUID id) {
        OrganizationUnit unit = requireUnit(tenantId, id);
        guard.requireAccessForCompany(unit.getCompanyId());
        return mapper.toResponse(unit);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationUnitResponse> search(
            OrganizationUnitSearchCriteria criteria, Pageable pageable) {
        if (criteria.companyId() != null) {
            guard.requireAccessForCompany(criteria.companyId());
        }
        Page<OrganizationUnitResponse> page =
                unitRepository
                        .findAll(OrganizationUnitSpecifications.matching(criteria), pageable)
                        .map(mapper::toResponse);
        if (criteria.companyId() == null) {
            guard.requireAccessForCompanies(
                    page.getContent().stream().map(OrganizationUnitResponse::companyId).toList());
        }
        return page;
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitTreeNode> getTree(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        List<OrganizationUnit> all =
                unitRepository.findAll(
                        OrganizationUnitSpecifications.matching(
                                new OrganizationUnitSearchCriteria(
                                        tenantId, companyId, null, null, null, null, null, null,
                                        null, null)));
        return mapper.toTree(all);
    }

    private OrganizationUnit requireUnit(UUID tenantId, UUID id) {
        return unitRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Organization unit not found"));
    }

    private OrganizationUnitType requireType(UUID tenantId, UUID id) {
        return unitTypeRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unit type not found"));
    }

    private String normaliseCountry(String raw) {
        return raw == null ? null : raw.toUpperCase(java.util.Locale.ROOT);
    }

    private void publish(OrganizationUnitEventType type, OrganizationUnit unit) {
        events.publishEvent(
                new OrganizationUnitEvent(
                        type,
                        unit.getId(),
                        unit.getTenantId(),
                        unit.getCompanyId(),
                        unit.getUnitType() != null ? unit.getUnitType().getId() : null,
                        unit.getParent() != null ? unit.getParent().getId() : null,
                        unit.getCode(),
                        unit.getName(),
                        unit.getStatus(),
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
