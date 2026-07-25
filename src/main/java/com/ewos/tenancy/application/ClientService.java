package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.ClientResponse;
import com.ewos.tenancy.api.dto.CreateClientRequest;
import com.ewos.tenancy.api.dto.UpdateClientRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ClientService {

    private final ClientRepository repository;
    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final ClientAccessGuard guard;
    private final TenancyMapper mapper;

    public ClientService(
            ClientRepository repository,
            TenantRepository tenantRepository,
            CompanyRepository companyRepository,
            ClientAccessGuard guard,
            TenancyMapper mapper) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public ClientResponse create(CreateClientRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Client code already in use for this tenant");
        }
        Tenant tenant =
                tenantRepository
                        .findById(request.tenantId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Tenant not found"));
        Client client = new Client();
        client.setTenant(tenant);
        client.setCode(request.code());
        client.setLegalName(request.legalName());
        client.setOnboardedAt(request.onboardedAt());
        return mapper.toResponse(repository.save(client));
    }

    public ClientResponse update(UUID tenantId, UUID id, UpdateClientRequest request) {
        Client client = require(tenantId, id);
        guard.requireAccess(id);
        if (request.legalName() != null) {
            client.setLegalName(request.legalName());
        }
        if (request.status() != null) {
            client.setStatus(request.status());
        }
        if (request.onboardedAt() != null) {
            client.setOnboardedAt(request.onboardedAt());
        }
        return mapper.toResponse(client);
    }

    @Transactional(readOnly = true)
    public ClientResponse getById(UUID tenantId, UUID id) {
        Client client = require(tenantId, id);
        guard.requireAccess(id);
        return mapper.toResponse(client);
    }

    /** Filtered to the caller's accessible clients unless they hold {@code CLIENT_ADMIN}. */
    @Transactional(readOnly = true)
    public List<ClientResponse> list(UUID tenantId) {
        if (guard.hasUnrestrictedAccess()) {
            return repository.findAllByTenantIdOrderByLegalNameAsc(tenantId).stream()
                    .map(mapper::toResponse)
                    .toList();
        }
        Set<UUID> accessible = guard.accessibleClientIds();
        if (accessible.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdInOrderByLegalNameAsc(List.copyOf(accessible)).stream()
                .filter(c -> c.getTenant().getId().equals(tenantId))
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        Client client = require(tenantId, id);
        guard.requireAccess(id);
        long referencing = companyRepository.countByClientId(id);
        if (referencing > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Client has " + referencing + " companies; close them before deletion");
        }
        repository.delete(client);
    }

    private Client require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Client not found"));
    }
}
