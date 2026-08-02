package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateIncomeTaxPolicyRequest;
import com.ewos.payroll.api.dto.IncomeTaxPolicyResponse;
import com.ewos.payroll.api.dto.UpdateIncomeTaxPolicyRequest;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.IncomeTaxPolicyRepository;
import com.ewos.shared.exception.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for the non-slab income-tax parameters (rebate, cess, deductions), per regime/FY. */
@Service
@Transactional
public class IncomeTaxPolicyService {

    private final IncomeTaxPolicyRepository repository;
    private final PayrollMapper mapper;

    public IncomeTaxPolicyService(IncomeTaxPolicyRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public IncomeTaxPolicyResponse create(CreateIncomeTaxPolicyRequest request) {
        IncomeTaxPolicy p = new IncomeTaxPolicy();
        p.setTenantId(request.tenantId());
        p.setRegime(request.regime());
        p.setFiscalYear(request.fiscalYear());
        if (request.rebateIncomeThreshold() != null) {
            p.setRebateIncomeThreshold(request.rebateIncomeThreshold());
        }
        if (request.rebateMaxAmount() != null) {
            p.setRebateMaxAmount(request.rebateMaxAmount());
        }
        if (request.cessRatePct() != null) {
            p.setCessRatePct(request.cessRatePct());
        }
        if (request.standardDeduction() != null) {
            p.setStandardDeduction(request.standardDeduction());
        }
        if (request.chapterViaMaxDeduction() != null) {
            p.setChapterViaMaxDeduction(request.chapterViaMaxDeduction());
        }
        if (request.housePropertyLossCap() != null) {
            p.setHousePropertyLossCap(request.housePropertyLossCap());
        }
        if (request.active() != null) {
            p.setActive(request.active());
        }
        return mapper.toResponse(repository.save(p));
    }

    public IncomeTaxPolicyResponse update(UUID tenantId, UUID id, UpdateIncomeTaxPolicyRequest r) {
        IncomeTaxPolicy p = require(tenantId, id);
        if (r.rebateIncomeThreshold() != null) {
            p.setRebateIncomeThreshold(r.rebateIncomeThreshold());
        }
        if (r.rebateMaxAmount() != null) {
            p.setRebateMaxAmount(r.rebateMaxAmount());
        }
        if (r.cessRatePct() != null) {
            p.setCessRatePct(r.cessRatePct());
        }
        if (r.standardDeduction() != null) {
            p.setStandardDeduction(r.standardDeduction());
        }
        if (r.chapterViaMaxDeduction() != null) {
            p.setChapterViaMaxDeduction(r.chapterViaMaxDeduction());
        }
        if (r.housePropertyLossCap() != null) {
            p.setHousePropertyLossCap(r.housePropertyLossCap());
        }
        if (r.active() != null) {
            p.setActive(r.active());
        }
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public IncomeTaxPolicyResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public IncomeTaxPolicyResponse forRegimeAndYear(
            UUID tenantId, TaxRegime regime, String fiscalYear) {
        return repository
                .findByTenantIdAndRegimeAndFiscalYearAndActiveTrue(tenantId, regime, fiscalYear)
                .map(mapper::toResponse)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Income tax policy not found"));
    }

    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    private IncomeTaxPolicy require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Income tax policy not found"));
    }
}
