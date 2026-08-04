package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateIncomeTaxSlabRequest;
import com.ewos.payroll.api.dto.IncomeTaxSlabResponse;
import com.ewos.payroll.api.dto.UpdateIncomeTaxSlabRequest;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxSlabRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for progressive income-tax brackets, per regime and fiscal year. Tenant-wide. */
@Service
@Transactional
public class IncomeTaxSlabService {

    private final IncomeTaxSlabRepository repository;
    private final PayrollMapper mapper;

    public IncomeTaxSlabService(IncomeTaxSlabRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public IncomeTaxSlabResponse create(CreateIncomeTaxSlabRequest request) {
        IncomeTaxSlab s = new IncomeTaxSlab();
        s.setTenantId(request.tenantId());
        s.setRegime(request.regime());
        s.setFiscalYear(request.fiscalYear());
        s.setMinIncome(request.minIncome());
        s.setMaxIncome(request.maxIncome());
        s.setRatePct(request.ratePct());
        if (request.active() != null) {
            s.setActive(request.active());
        }
        return mapper.toResponse(repository.save(s));
    }

    public IncomeTaxSlabResponse update(UUID tenantId, UUID id, UpdateIncomeTaxSlabRequest r) {
        IncomeTaxSlab s = require(tenantId, id);
        if (r.minIncome() != null) {
            s.setMinIncome(r.minIncome());
        }
        if (r.maxIncome() != null) {
            s.setMaxIncome(r.maxIncome());
        }
        if (r.ratePct() != null) {
            s.setRatePct(r.ratePct());
        }
        if (r.active() != null) {
            s.setActive(r.active());
        }
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public IncomeTaxSlabResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<IncomeTaxSlabResponse> forRegimeAndYear(
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

    private IncomeTaxSlab require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Income tax slab not found"));
    }
}
