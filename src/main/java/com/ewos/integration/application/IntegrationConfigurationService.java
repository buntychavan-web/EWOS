package com.ewos.integration.application;

import com.ewos.integration.api.IntegrationMapper;
import com.ewos.integration.api.dto.CreateIntegrationConfigurationRequest;
import com.ewos.integration.api.dto.IntegrationConfigurationResponse;
import com.ewos.integration.api.dto.UpdateIntegrationConfigurationRequest;
import com.ewos.integration.domain.IntegrationConfiguration;
import com.ewos.integration.infrastructure.persistence.IntegrationConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for {@link IntegrationConfiguration} — which adapter + settings a company uses per exchange
 * type.
 */
@Service
@Transactional
public class IntegrationConfigurationService {

    private final IntegrationConfigurationRepository repository;
    private final ClientAccessGuard guard;
    private final IntegrationMapper mapper;

    public IntegrationConfigurationService(
            IntegrationConfigurationRepository repository,
            ClientAccessGuard guard,
            IntegrationMapper mapper) {
        this.repository = repository;
        this.guard = guard;
        this.mapper = mapper;
    }

    public IntegrationConfigurationResponse create(CreateIntegrationConfigurationRequest request) {
        guard.requireAccessForCompany(request.companyId());
        repository
                .findByTenantIdAndCompanyIdAndExchangeTypeIgnoreCaseAndActiveTrue(
                        request.tenantId(), request.companyId(), request.exchangeType())
                .ifPresent(
                        existing -> {
                            throw new ApiException(
                                    HttpStatus.CONFLICT,
                                    "An active integration configuration already exists for exchangeType '"
                                            + request.exchangeType()
                                            + "'");
                        });
        IntegrationConfiguration c = new IntegrationConfiguration();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setExchangeType(request.exchangeType());
        c.setAdapterType(request.adapterType());
        c.setConfigJson(request.configJson());
        c.setActive(true);
        return mapper.toResponse(repository.save(c));
    }

    public IntegrationConfigurationResponse update(
            UUID tenantId, UUID id, UpdateIntegrationConfigurationRequest request) {
        IntegrationConfiguration c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        if (request.configJson() != null) {
            c.setConfigJson(request.configJson());
        }
        if (request.active() != null) {
            c.setActive(request.active());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public IntegrationConfigurationResponse getById(UUID tenantId, UUID id) {
        IntegrationConfiguration c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<IntegrationConfigurationResponse> forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository
                .findAllByTenantIdAndCompanyIdOrderByExchangeTypeAsc(tenantId, companyId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        IntegrationConfiguration c = require(tenantId, id);
        guard.requireAccessForCompany(c.getCompanyId());
        repository.delete(c);
    }

    private IntegrationConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Integration configuration not found"));
    }
}
