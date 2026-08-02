package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateLwfConfigurationRequest;
import com.ewos.payroll.api.dto.LwfConfigurationResponse;
import com.ewos.payroll.api.dto.UpdateLwfConfigurationRequest;
import com.ewos.payroll.domain.LwfConfiguration;
import com.ewos.payroll.domain.StatutoryJurisdiction;
import com.ewos.payroll.infrastructure.persistence.LwfConfigurationRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryJurisdictionRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for state-wise Labour Welfare Fund schemes. Tenant-wide (no company scoping). */
@Service
@Transactional
public class LwfConfigurationService {

    private final LwfConfigurationRepository repository;
    private final StatutoryJurisdictionRepository jurisdictions;
    private final PayrollMapper mapper;

    public LwfConfigurationService(
            LwfConfigurationRepository repository,
            StatutoryJurisdictionRepository jurisdictions,
            PayrollMapper mapper) {
        this.repository = repository;
        this.jurisdictions = jurisdictions;
        this.mapper = mapper;
    }

    public LwfConfigurationResponse create(CreateLwfConfigurationRequest request) {
        StatutoryJurisdiction jurisdiction = requireJurisdiction(request.jurisdictionId());
        LwfConfiguration c = new LwfConfiguration();
        c.setTenantId(request.tenantId());
        c.setJurisdiction(jurisdiction);
        c.setEmployeeContribution(request.employeeContribution());
        c.setEmployerContribution(request.employerContribution());
        c.setRemittanceMonths(request.remittanceMonths());
        c.setEffectiveFrom(request.effectiveFrom());
        c.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            c.setActive(request.active());
        }
        return mapper.toResponse(repository.save(c));
    }

    public LwfConfigurationResponse update(
            UUID tenantId, UUID id, UpdateLwfConfigurationRequest r) {
        LwfConfiguration c = require(tenantId, id);
        if (r.employeeContribution() != null) {
            c.setEmployeeContribution(r.employeeContribution());
        }
        if (r.employerContribution() != null) {
            c.setEmployerContribution(r.employerContribution());
        }
        if (r.remittanceMonths() != null) {
            c.setRemittanceMonths(r.remittanceMonths());
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
    public LwfConfigurationResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<LwfConfigurationResponse> forJurisdiction(UUID tenantId, UUID jurisdictionId) {
        return repository.findActiveForJurisdiction(tenantId, jurisdictionId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private LwfConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "LWF configuration not found"));
    }

    private StatutoryJurisdiction requireJurisdiction(UUID id) {
        return jurisdictions
                .findById(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.BAD_REQUEST, "Jurisdiction not found"));
    }
}
