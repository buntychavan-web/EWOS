package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateIncomeTaxSurchargeSlabRequest;
import com.ewos.payroll.api.dto.IncomeTaxSurchargeSlabResponse;
import com.ewos.payroll.api.dto.UpdateIncomeTaxSurchargeSlabRequest;
import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxSurchargeSlabRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for income-tax surcharge brackets, per regime and fiscal year. Tenant-wide. */
@Service
@Transactional
public class IncomeTaxSurchargeSlabService {

    private final IncomeTaxSurchargeSlabRepository repository;
    private final PayrollMapper mapper;

    public IncomeTaxSurchargeSlabService(
            IncomeTaxSurchargeSlabRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public IncomeTaxSurchargeSlabResponse create(CreateIncomeTaxSurchargeSlabRequest request) {
        IncomeTaxSurchargeSlab s = new IncomeTaxSurchargeSlab();
        s.setTenantId(request.tenantId());
        s.setRegime(request.regime());
        s.setFiscalYear(request.fiscalYear());
        s.setMinIncome(request.minIncome());
        s.setMaxIncome(request.maxIncome());
        s.setSurchargeRatePct(request.surchargeRatePct());
        if (request.active() != null) {
            s.setActive(request.active());
        }
        return mapper.toResponse(repository.save(s));
    }

    public IncomeTaxSurchargeSlabResponse update(
            UUID tenantId, UUID id, UpdateIncomeTaxSurchargeSlabRequest r) {
        IncomeTaxSurchargeSlab s = require(tenantId, id);
        if (r.minIncome() != null) {
            s.setMinIncome(r.minIncome());
        }
        if (r.maxIncome() != null) {
            s.setMaxIncome(r.maxIncome());
        }
        if (r.surchargeRatePct() != null) {
            s.setSurchargeRatePct(r.surchargeRatePct());
        }
        if (r.active() != null) {
            s.setActive(r.active());
        }
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public IncomeTaxSurchargeSlabResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<IncomeTaxSurchargeSlabResponse> forRegimeAndYear(
            UUID tenantId, TaxRegime regime, String fiscalYear) {
        return repository
                .findAllByTenantIdAndRegimeAndFiscalYearAndActiveTrueOrderByMinIncomeAsc(
                        tenantId, regime, fiscalYear)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private IncomeTaxSurchargeSlab require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Income tax surcharge slab not found"));
    }
}
