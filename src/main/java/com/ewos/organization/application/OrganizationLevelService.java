package com.ewos.organization.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.organization.api.dto.CreateOrganizationLevelRequest;
import com.ewos.organization.api.dto.OrganizationLevelResponse;
import com.ewos.organization.api.dto.UpdateOrganizationLevelRequest;
import com.ewos.organization.domain.OrganizationLevel;
import com.ewos.organization.infrastructure.persistence.OrganizationLevelRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationNodeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for configurable organization levels. Nothing about levels is hard-coded. */
@Service
@Transactional
public class OrganizationLevelService {

    private final OrganizationLevelRepository levelRepository;
    private final OrganizationNodeRepository nodeRepository;
    private final TenantResolver tenantResolver;

    public OrganizationLevelService(
            OrganizationLevelRepository levelRepository,
            OrganizationNodeRepository nodeRepository,
            TenantResolver tenantResolver) {
        this.levelRepository = levelRepository;
        this.nodeRepository = nodeRepository;
        this.tenantResolver = tenantResolver;
    }

    public OrganizationLevelResponse create(CreateOrganizationLevelRequest req) {
        EffectiveDateValidator.requireOrdered(req.effectiveFrom(), null);
        Tenant tenant = tenantResolver.resolve(req.tenantId());
        levelRepository
                .findByTenantAndCode(tenant, req.code())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "Level code '" + req.code() + "' already exists in tenant");
                        });

        OrganizationLevel parent = null;
        if (req.parentLevelId() != null) {
            parent = requireLevel(req.parentLevelId());
            if (!parent.getTenant().getId().equals(tenant.getId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Parent level belongs to a different tenant");
            }
        }

        OrganizationLevel level = new OrganizationLevel();
        level.setTenant(tenant);
        level.setCode(req.code());
        level.setName(req.name());
        level.setDisplaySequence(req.displaySequence());
        level.setParentLevel(parent);
        level.setEffectiveFrom(req.effectiveFrom());
        level.setActive(true);
        return OrganizationMapper.toLevel(levelRepository.save(level));
    }

    public OrganizationLevelResponse update(UUID id, UpdateOrganizationLevelRequest req) {
        OrganizationLevel level = requireLevel(id);
        level.setName(req.name());
        level.setDisplaySequence(req.displaySequence());
        return OrganizationMapper.toLevel(level);
    }

    public OrganizationLevelResponse setActive(UUID id, boolean active) {
        OrganizationLevel level = requireLevel(id);
        level.setActive(active);
        return OrganizationMapper.toLevel(level);
    }

    public void softDelete(UUID id) {
        OrganizationLevel level = requireLevel(id);
        if (levelRepository.existsByParentLevel(level)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Level has child levels; delete or reparent them first");
        }
        if (nodeRepository.existsByLevel(level)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Level is in use by nodes; reassign or delete them first");
        }
        levelRepository.delete(level);
    }

    @Transactional(readOnly = true)
    public OrganizationLevelResponse getById(UUID id) {
        return OrganizationMapper.toLevel(requireLevel(id));
    }

    @Transactional(readOnly = true)
    public List<OrganizationLevelResponse> list(UUID tenantId) {
        Tenant tenant = tenantResolver.resolve(tenantId);
        return levelRepository.findByTenantOrderByDisplaySequenceAsc(tenant).stream()
                .map(OrganizationMapper::toLevel)
                .toList();
    }

    private OrganizationLevel requireLevel(UUID id) {
        return levelRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Organization level not found"));
    }
}
