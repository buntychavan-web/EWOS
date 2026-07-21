package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.StatutoryDeductionResponse;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.StatutoryClassifier;
import com.ewos.payroll.domain.StatutoryDeduction;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans finalized payslips for statutory deduction lines and materialises one {@link
 * StatutoryDeduction} row per (payslip, statutory code). Employer contribution stays zero here —
 * the classifier's built-in defaults treat every recognised deduction as an employee-side amount.
 * Tenants that also need employer-side contributions materialised should extend the classifier or
 * post the amount via the read-write endpoint.
 */
@Service
@Transactional
public class StatutoryDeductionService {

    private final StatutoryDeductionRepository repository;
    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final StatutoryClassifier classifier;
    private final PayrollMapper mapper;

    public StatutoryDeductionService(
            StatutoryDeductionRepository repository,
            PayrollRunRepository runs,
            PayslipRepository payslips,
            StatutoryClassifier classifier,
            PayrollMapper mapper) {
        this.repository = repository;
        this.runs = runs;
        this.payslips = payslips;
        this.classifier = classifier;
        this.mapper = mapper;
    }

    /**
     * Extract statutory deductions from every payslip on the given run. Idempotent per (payslip,
     * code) — the unique index on {@code payslip_id, LOWER(code)} guarantees the DB rejects a
     * second insert; this method skips codes that already exist.
     */
    public int extractForRun(UUID tenantId, UUID runId) {
        PayrollRun run =
                runs.findByIdAndTenantId(runId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Payroll run not found"));
        int inserted = 0;
        for (Payslip slip : payslips.findAllForRun(tenantId, runId)) {
            Map<String, StatutoryDeduction> existingByCode = new HashMap<>();
            for (StatutoryDeduction d : repository.findAllForPayslip(tenantId, slip.getId())) {
                existingByCode.put(d.getCode().toLowerCase(java.util.Locale.ROOT), d);
            }
            int periodMonth =
                    slip.getPeriodStart() == null
                            ? 0
                            : slip.getPeriodStart().getYear() * 100
                                    + slip.getPeriodStart().getMonthValue();
            BigDecimal basic =
                    slip.getBasicEffective() != null ? slip.getBasicEffective() : BigDecimal.ZERO;

            for (PayslipLine line : slip.getLines()) {
                if (line.getKind() != PayComponentKind.DEDUCTION) {
                    continue;
                }
                var cls = classifier.classify(line.getComponentCodeSnapshot());
                if (cls.isEmpty()) {
                    continue;
                }
                String codeKey = cls.get().code().toLowerCase(java.util.Locale.ROOT);
                if (existingByCode.containsKey(codeKey)) {
                    continue;
                }
                StatutoryDeduction d = new StatutoryDeduction();
                d.setTenantId(tenantId);
                d.setCompanyId(slip.getCompanyId());
                d.setPayrollRun(run);
                d.setPayslip(slip);
                d.setEmployee(slip.getEmployee());
                d.setJurisdiction(cls.get().jurisdiction());
                d.setCode(cls.get().code());
                d.setPeriodMonth(periodMonth);
                d.setTaxableBase(basic);
                d.setEmployeeContribution(line.getAmount());
                d.setEmployerContribution(BigDecimal.ZERO);
                d.setTotalAmount(line.getAmount());
                d.setCurrency(slip.getCurrency());
                repository.save(d);
                inserted++;
            }
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public StatutoryDeductionResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Statutory deduction not found")));
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forPayslip(UUID tenantId, UUID payslipId) {
        return repository.findAllForPayslip(tenantId, payslipId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forRun(UUID tenantId, UUID runId) {
        return repository.findAllForRun(tenantId, runId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forEmployeeMonth(
            UUID tenantId, UUID employeeId, int periodMonth) {
        return repository.findForEmployeeMonth(tenantId, employeeId, periodMonth).stream()
                .map(mapper::toResponse)
                .toList();
    }
}
