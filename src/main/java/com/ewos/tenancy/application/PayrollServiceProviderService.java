package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreatePayrollServiceProviderRequest;
import com.ewos.tenancy.api.dto.PayrollServiceProviderResponse;
import com.ewos.tenancy.api.dto.UpdatePayrollServiceProviderRequest;
import com.ewos.tenancy.domain.PayrollServiceProvider;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.PayrollServiceProviderRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PayrollServiceProviderService {

    private final PayrollServiceProviderRepository repository;
    private final TenantRepository tenantRepository;
    private final TenancyMapper mapper;

    public PayrollServiceProviderService(
            PayrollServiceProviderRepository repository,
            TenantRepository tenantRepository,
            TenancyMapper mapper) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
    }

    public PayrollServiceProviderResponse create(CreatePayrollServiceProviderRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Provider code already in use for this tenant");
        }
        Tenant tenant =
                tenantRepository
                        .findById(request.tenantId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
        PayrollServiceProvider provider = new PayrollServiceProvider();
        provider.setTenant(tenant);
        provider.setCode(request.code());
        provider.setName(request.name());
        return mapper.toResponse(repository.save(provider));
    }

    public PayrollServiceProviderResponse update(
            UUID tenantId, UUID id, UpdatePayrollServiceProviderRequest request) {
        PayrollServiceProvider provider = require(tenantId, id);
        if (request.name() != null) {
            provider.setName(request.name());
        }
        if (request.status() != null) {
            provider.setStatus(request.status());
        }
        return mapper.toResponse(provider);
    }

    @Transactional(readOnly = true)
    public PayrollServiceProviderResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayrollServiceProviderResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private PayrollServiceProvider require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
    }
}
