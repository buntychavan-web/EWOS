package com.ewos.integration.application;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.ClientGoLiveConfigurationResponse;
import com.ewos.integration.api.dto.CreateClientGoLiveConfigurationRequest;
import com.ewos.integration.api.dto.UpdateClientGoLiveConfigurationRequest;
import com.ewos.integration.domain.ClientGoLiveConfiguration;
import com.ewos.integration.infrastructure.persistence.ClientGoLiveConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.4 — Client Go-Live Configuration. Tracks readiness of the outsourced payroll engagement
 * (Company) to go live: one configuration per Company, a target date, and a coarse status
 * (PLANNING/READY/LIVE/SUSPENDED).
 */
@Service
@Transactional
public class ClientGoLiveConfigurationService {

    private final ClientGoLiveConfigurationRepository repository;
    private final ClientAccessGuard guard;
    private final IntegrationMapper mapper;

    public ClientGoLiveConfigurationService(
            ClientGoLiveConfigurationRepository repository,
            ClientAccessGuard guard,
            IntegrationMapper mapper) {
        this.repository = repository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public ClientGoLiveConfigurationResponse create(
            CreateClientGoLiveConfigurationRequest request) {
        guard.requireAccessForCompany(request.companyId());
        if (repository.existsByCompanyId(request.companyId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A go-live configuration already exists for this company");
        }
        ClientGoLiveConfiguration c = new ClientGoLiveConfiguration();
        c.setTenantId(request.tenantId());
        c.setClientId(request.clientId());
        c.setCompanyId(request.companyId());
        c.setGoLiveDate(request.goLiveDate());
        c.setNotes(request.notes());
        return mapper.toResponse(repository.save(c));
    }

    public ClientGoLiveConfigurationResponse update(
            UUID tenantId, UUID id, UpdateClientGoLiveConfigurationRequest request) {
        ClientGoLiveConfiguration c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        if (request.goLiveDate() != null) {
            c.setGoLiveDate(request.goLiveDate());
        }
        if (request.status() != null) {
            c.setStatus(request.status());
        }
        if (request.notes() != null) {
            c.setNotes(request.notes());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public ClientGoLiveConfigurationResponse getById(UUID tenantId, UUID id) {
        ClientGoLiveConfiguration c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public ClientGoLiveConfigurationResponse forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository
                .findByTenantIdAndCompanyId(tenantId, companyId)
                .map(mapper::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No go-live configuration for this company"));
    }

    @Transactional(readOnly = true)
    public List<ClientGoLiveConfigurationResponse> forClient(UUID tenantId, UUID clientId) {
        guard.requireAccess(clientId);
        return repository
                .findAllByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private ClientGoLiveConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Go-live configuration not found"));
    }
}
