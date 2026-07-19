package com.ewos.organization.application;

import com.ewos.organization.api.OrganizationMapper;
import com.ewos.organization.api.dto.CreateOrganizationUnitTypeRequest;
import com.ewos.organization.api.dto.OrganizationUnitTypeResponse;
import com.ewos.organization.api.dto.UpdateOrganizationUnitTypeRequest;
import com.ewos.organization.domain.OrganizationUnitType;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitTypeRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrganizationUnitTypeService {

    private final OrganizationUnitTypeRepository repository;
    private final OrganizationMapper mapper;

    public OrganizationUnitTypeService(
            OrganizationUnitTypeRepository repository, OrganizationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public OrganizationUnitTypeResponse create(CreateOrganizationUnitTypeRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Unit type code already in use for this tenant");
        }
        OrganizationUnitType type = new OrganizationUnitType();
        type.setTenantId(request.tenantId());
        type.setCode(request.code());
        type.setName(request.name());
        type.setDescription(request.description());
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            type.setActive(request.active());
        }
        return mapper.toResponse(repository.save(type));
    }

    public OrganizationUnitTypeResponse update(
            UUID tenantId, UUID id, UpdateOrganizationUnitTypeRequest request) {
        OrganizationUnitType type = require(tenantId, id);
        if (request.name() != null) {
            type.setName(request.name());
        }
        if (request.description() != null) {
            type.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            type.setActive(request.active());
        }
        return mapper.toResponse(type);
    }

    @Transactional(readOnly = true)
    public OrganizationUnitTypeResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<OrganizationUnitTypeResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private OrganizationUnitType require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unit type not found"));
    }
}
