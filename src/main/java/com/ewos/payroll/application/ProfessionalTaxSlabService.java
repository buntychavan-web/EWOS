package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateProfessionalTaxSlabRequest;
import com.ewos.payroll.api.dto.ProfessionalTaxSlabResponse;
import com.ewos.payroll.api.dto.UpdateProfessionalTaxSlabRequest;
import com.ewos.payroll.domain.ProfessionalTaxSlab;
import com.ewos.payroll.domain.StatutoryJurisdiction;
import com.ewos.payroll.infrastructure.persistence.ProfessionalTaxSlabRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryJurisdictionRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for state-wise Professional Tax slabs. Tenant-wide (no company scoping). */
@Service
@Transactional
public class ProfessionalTaxSlabService {

    private final ProfessionalTaxSlabRepository repository;
    private final StatutoryJurisdictionRepository jurisdictions;
    private final PayrollMapper mapper;

    public ProfessionalTaxSlabService(
            ProfessionalTaxSlabRepository repository,
            StatutoryJurisdictionRepository jurisdictions,
            PayrollMapper mapper) {
        this.repository = repository;
        this.jurisdictions = jurisdictions;
        this.mapper = mapper;
    }

    public ProfessionalTaxSlabResponse create(CreateProfessionalTaxSlabRequest request) {
        StatutoryJurisdiction jurisdiction = requireJurisdiction(request.jurisdictionId());
        ProfessionalTaxSlab s = new ProfessionalTaxSlab();
        s.setTenantId(request.tenantId());
        s.setJurisdiction(jurisdiction);
        s.setGender(request.gender());
        s.setMinMonthlyIncome(request.minMonthlyIncome());
        s.setMaxMonthlyIncome(request.maxMonthlyIncome());
        s.setMonthlyTaxAmount(request.monthlyTaxAmount());
        s.setAnnualCapAmount(request.annualCapAmount());
        s.setEffectiveFrom(request.effectiveFrom());
        s.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            s.setActive(request.active());
        }
        return mapper.toResponse(repository.save(s));
    }

    public ProfessionalTaxSlabResponse update(
            UUID tenantId, UUID id, UpdateProfessionalTaxSlabRequest r) {
        ProfessionalTaxSlab s = require(tenantId, id);
        if (r.gender() != null) {
            s.setGender(r.gender());
        }
        if (r.minMonthlyIncome() != null) {
            s.setMinMonthlyIncome(r.minMonthlyIncome());
        }
        if (r.maxMonthlyIncome() != null) {
            s.setMaxMonthlyIncome(r.maxMonthlyIncome());
        }
        if (r.monthlyTaxAmount() != null) {
            s.setMonthlyTaxAmount(r.monthlyTaxAmount());
        }
        if (r.annualCapAmount() != null) {
            s.setAnnualCapAmount(r.annualCapAmount());
        }
        if (r.effectiveFrom() != null) {
            s.setEffectiveFrom(r.effectiveFrom());
        }
        if (r.effectiveTo() != null) {
            s.setEffectiveTo(r.effectiveTo());
        }
        if (r.active() != null) {
            s.setActive(r.active());
        }
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public ProfessionalTaxSlabResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ProfessionalTaxSlabResponse> forJurisdiction(UUID tenantId, UUID jurisdictionId) {
        return repository.findActiveForJurisdiction(tenantId, jurisdictionId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private ProfessionalTaxSlab require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Professional Tax slab not found"));
    }

    private StatutoryJurisdiction requireJurisdiction(UUID id) {
        return jurisdictions
                .findById(id)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.BAD_REQUEST, "Jurisdiction not found"));
    }
}
