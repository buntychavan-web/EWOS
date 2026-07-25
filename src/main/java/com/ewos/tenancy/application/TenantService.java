package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateTenantRequest;
import com.ewos.tenancy.api.dto.TenantResponse;
import com.ewos.tenancy.api.dto.UpdateTenantRequest;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantService {

    private final TenantRepository repository;
    private final TenancyMapper mapper;

    public TenantService(TenantRepository repository, TenancyMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public TenantResponse create(CreateTenantRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Tenant code already in use");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(request.code());
        tenant.setName(request.name());
        return mapper.toResponse(repository.save(tenant));
    }

    public TenantResponse update(UUID id, UpdateTenantRequest request) {
        Tenant tenant = require(id);
        if (request.name() != null) {
            tenant.setName(request.name());
        }
        if (request.status() != null) {
            tenant.setStatus(request.status());
        }
        return mapper.toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(UUID id) {
        return mapper.toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(mapper::toResponse).toList();
    }

    public void delete(UUID id) {
        repository.delete(require(id));
    }

    private Tenant require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
    }
}
