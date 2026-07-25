package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CompanyResponse;
import com.ewos.tenancy.api.dto.CreateCompanyRequest;
import com.ewos.tenancy.api.dto.UpdateCompanyRequest;
import com.ewos.tenancy.domain.Client;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.infrastructure.persistence.ClientRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Also backs the frontend's Company Switcher (Sprint 14.1 scope) — {@link #list(UUID)} is exactly
 * the "companies accessible to the current user" query it needs, for free, from the same Chinese
 * Wall filtering every other Tenancy list endpoint already applies.
 */
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository repository;
    private final ClientRepository clientRepository;
    private final ClientAccessGuard guard;
    private final TenancyMapper mapper;

    public CompanyService(
            CompanyRepository repository,
            ClientRepository clientRepository,
            ClientAccessGuard guard,
            TenancyMapper mapper) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public CompanyResponse create(CreateCompanyRequest request) {
        Client client =
                clientRepository
                        .findById(request.clientId())
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "Client not found"));
        guard.requireAccess(client.getId());
        if (repository.existsByClientIdAndCodeIgnoreCase(request.clientId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Company code already in use for this client");
        }
        Company company = new Company();
        company.setTenantId(request.tenantId());
        company.setClient(client);
        company.setCode(request.code());
        company.setName(request.name());
        company.setCountryCode(request.countryCode());
        return mapper.toResponse(repository.save(company));
    }

    public CompanyResponse update(UUID tenantId, UUID id, UpdateCompanyRequest request) {
        Company company = require(tenantId, id);
        guard.requireAccess(company.getClient().getId());
        if (request.name() != null) {
            company.setName(request.name());
        }
        if (request.countryCode() != null) {
            company.setCountryCode(request.countryCode());
        }
        if (request.status() != null) {
            company.setStatus(request.status());
        }
        return mapper.toResponse(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID tenantId, UUID id) {
        Company company = require(tenantId, id);
        guard.requireAccess(company.getClient().getId());
        return mapper.toResponse(company);
    }

    /**
     * Filtered to companies under the caller's accessible clients — powers the Company Switcher.
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> list(UUID tenantId) {
        if (guard.hasUnrestrictedAccess()) {
            return repository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                    .map(mapper::toResponse)
                    .toList();
        }
        Set<UUID> accessible = guard.accessibleClientIds();
        if (accessible.isEmpty()) {
            return List.of();
        }
        return repository.findAllByClientIdInOrderByNameAsc(List.copyOf(accessible)).stream()
                .filter(c -> c.getTenantId().equals(tenantId))
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        Company company = require(tenantId, id);
        guard.requireAccess(company.getClient().getId());
        repository.delete(company);
    }

    private Company require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
    }
}
