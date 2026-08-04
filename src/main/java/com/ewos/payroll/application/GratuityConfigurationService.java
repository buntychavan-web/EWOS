package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateGratuityConfigurationRequest;
import com.ewos.payroll.api.dto.GratuityConfigurationResponse;
import com.ewos.payroll.api.dto.UpdateGratuityConfigurationRequest;
import com.ewos.payroll.domain.GratuityConfiguration;
import com.ewos.payroll.infrastructure.persistence.GratuityConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for Gratuity configuration. {@code companyId == null} is the tenant-wide default. */
@Service
@Transactional
public class GratuityConfigurationService {

    private final GratuityConfigurationRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public GratuityConfigurationService(
            GratuityConfigurationRepository repository,
            PayrollMapper mapper,
            ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public GratuityConfigurationResponse create(CreateGratuityConfigurationRequest request) {
        if (request.companyId() != null) {
            guard.requireAccessForCompany(request.companyId());
        }
        GratuityConfiguration c = new GratuityConfiguration();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setStatutoryCeiling(request.statutoryCeiling());
        if (request.rateNumerator() != null) {
            c.setRateNumerator(request.rateNumerator());
        }
        if (request.rateDenominator() != null) {
            c.setRateDenominator(request.rateDenominator());
        }
        if (request.minYearsEligibility() != null) {
            c.setMinYearsEligibility(request.minYearsEligibility());
        }
        c.setEffectiveFrom(request.effectiveFrom());
        c.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            c.setActive(request.active());
        }
        return mapper.toResponse(repository.save(c));
    }

    public GratuityConfigurationResponse update(
            UUID tenantId, UUID id, UpdateGratuityConfigurationRequest r) {
        GratuityConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        if (r.statutoryCeiling() != null) {
            c.setStatutoryCeiling(r.statutoryCeiling());
        }
        if (r.rateNumerator() != null) {
            c.setRateNumerator(r.rateNumerator());
        }
        if (r.rateDenominator() != null) {
            c.setRateDenominator(r.rateDenominator());
        }
        if (r.minYearsEligibility() != null) {
            c.setMinYearsEligibility(r.minYearsEligibility());
        }
        if (r.effectiveFrom() != null) {
            c.setEffectiveFrom(r.effectiveFrom());
        }
        if (r.effectiveTo() != null) {
            c.setEffectiveTo(r.effectiveTo());
        }
        if (r.active() != null) {
            c.setActive(r.active());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public GratuityConfigurationResponse getById(UUID tenantId, UUID id) {
        GratuityConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<GratuityConfigurationResponse> forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository.findCandidates(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        GratuityConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        repository.delete(c);
    }

    private GratuityConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Gratuity configuration not found"));
    }
}
