package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateServiceOfferingRequest;
import com.ewos.tenancy.api.dto.ServiceOfferingResponse;
import com.ewos.tenancy.api.dto.UpdateServiceOfferingRequest;
import com.ewos.tenancy.domain.ServiceOffering;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.ServiceOfferingRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ServiceOfferingService {

    private final ServiceOfferingRepository repository;
    private final TenantRepository tenantRepository;
    private final TenancyMapper mapper;

    public ServiceOfferingService(
            ServiceOfferingRepository repository,
            TenantRepository tenantRepository,
            TenancyMapper mapper) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.mapper = mapper;
    }

    public ServiceOfferingResponse create(CreateServiceOfferingRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Service code already in use for this tenant");
        }
        Tenant tenant =
                tenantRepository
                        .findById(request.tenantId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
        ServiceOffering service = new ServiceOffering();
        service.setTenant(tenant);
        service.setCode(request.code());
        service.setName(request.name());
        service.setDescription(request.description());
        service.setCategory(request.category());
        if (request.sortOrder() != null) {
            service.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            service.setActive(request.active());
        }
        return mapper.toResponse(repository.save(service));
    }

    public ServiceOfferingResponse update(
            UUID tenantId, UUID id, UpdateServiceOfferingRequest request) {
        ServiceOffering service = require(tenantId, id);
        if (request.name() != null) {
            service.setName(request.name());
        }
        if (request.description() != null) {
            service.setDescription(request.description());
        }
        if (request.category() != null) {
            service.setCategory(request.category());
        }
        if (request.sortOrder() != null) {
            service.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            service.setActive(request.active());
        }
        return mapper.toResponse(service);
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private ServiceOffering require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Service offering not found"));
    }
}
