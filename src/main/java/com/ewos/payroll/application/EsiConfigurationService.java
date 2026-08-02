package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEsiConfigurationRequest;
import com.ewos.payroll.api.dto.EsiConfigurationResponse;
import com.ewos.payroll.api.dto.UpdateEsiConfigurationRequest;
import com.ewos.payroll.domain.EsiConfiguration;
import com.ewos.payroll.infrastructure.persistence.EsiConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for ESI configuration. {@code companyId == null} is the tenant-wide default. */
@Service
@Transactional
public class EsiConfigurationService {

    private final EsiConfigurationRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public EsiConfigurationService(
            EsiConfigurationRepository repository, PayrollMapper mapper, ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public EsiConfigurationResponse create(CreateEsiConfigurationRequest request) {
        if (request.companyId() != null) {
            guard.requireAccessForCompany(request.companyId());
        }
        EsiConfiguration c = new EsiConfiguration();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setWageThreshold(request.wageThreshold());
        c.setEmployeeRatePct(request.employeeRatePct());
        c.setEmployerRatePct(request.employerRatePct());
        c.setEffectiveFrom(request.effectiveFrom());
        c.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            c.setActive(request.active());
        }
        return mapper.toResponse(repository.save(c));
    }

    public EsiConfigurationResponse update(
            UUID tenantId, UUID id, UpdateEsiConfigurationRequest r) {
        EsiConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        if (r.wageThreshold() != null) {
            c.setWageThreshold(r.wageThreshold());
        }
        if (r.employeeRatePct() != null) {
            c.setEmployeeRatePct(r.employeeRatePct());
        }
        if (r.employerRatePct() != null) {
            c.setEmployerRatePct(r.employerRatePct());
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
    public EsiConfigurationResponse getById(UUID tenantId, UUID id) {
        EsiConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<EsiConfigurationResponse> forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository.findCandidates(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        EsiConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        repository.delete(c);
    }

    private EsiConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "ESI configuration not found"));
    }
}
