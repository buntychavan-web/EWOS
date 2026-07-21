package com.ewos.employee.application;

import com.ewos.employee.api.EmployeeMapper;
import com.ewos.employee.api.dto.CreateEmploymentTypeRequest;
import com.ewos.employee.api.dto.EmploymentTypeResponse;
import com.ewos.employee.api.dto.UpdateEmploymentTypeRequest;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.employee.infrastructure.persistence.EmploymentTypeRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmploymentTypeService {

    private final EmploymentTypeRepository repository;
    private final EmployeeMapper mapper;

    public EmploymentTypeService(EmploymentTypeRepository repository, EmployeeMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public EmploymentTypeResponse create(CreateEmploymentTypeRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Employment type code already in use for this tenant");
        }
        EmploymentType t = new EmploymentType();
        t.setTenantId(request.tenantId());
        t.setCode(request.code());
        t.setName(request.name());
        t.setDescription(request.description());
        if (request.sortOrder() != null) {
            t.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            t.setActive(request.active());
        }
        return mapper.toResponse(repository.save(t));
    }

    public EmploymentTypeResponse update(
            UUID tenantId, UUID id, UpdateEmploymentTypeRequest request) {
        EmploymentType t = require(tenantId, id);
        if (request.name() != null) {
            t.setName(request.name());
        }
        if (request.description() != null) {
            t.setDescription(request.description());
        }
        if (request.sortOrder() != null) {
            t.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            t.setActive(request.active());
        }
        return mapper.toResponse(t);
    }

    @Transactional(readOnly = true)
    public EmploymentTypeResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<EmploymentTypeResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private EmploymentType require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Employment type not found"));
    }
}
