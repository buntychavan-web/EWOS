package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.RollUpChallanRequest;
import com.ewos.payroll.api.dto.StatutoryChallanResponse;
import com.ewos.payroll.domain.StatutoryChallan;
import com.ewos.payroll.domain.StatutoryChallanStatus;
import com.ewos.payroll.domain.StatutoryDeduction;
import com.ewos.payroll.infrastructure.persistence.StatutoryChallanRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolls a set of {@link StatutoryDeduction} rows into a monthly {@link StatutoryChallan}.
 * Idempotent per (tenant, company, jurisdiction, code, month) — a second roll-up call reuses the
 * existing row and appends any newly-eligible deductions.
 */
@Service
@Transactional
public class StatutoryChallanService {

    private final StatutoryChallanRepository challans;
    private final StatutoryDeductionRepository deductions;
    private final PayrollMapper mapper;

    public StatutoryChallanService(
            StatutoryChallanRepository challans,
            StatutoryDeductionRepository deductions,
            PayrollMapper mapper) {
        this.challans = challans;
        this.deductions = deductions;
        this.mapper = mapper;
    }

    public StatutoryChallanResponse rollUp(RollUpChallanRequest request) {
        StatutoryChallan c =
                challans.findByScope(
                                request.tenantId(),
                                request.companyId(),
                                request.jurisdiction(),
                                request.code(),
                                request.periodMonth())
                        .orElseGet(
                                () -> {
                                    StatutoryChallan fresh = new StatutoryChallan();
                                    fresh.setTenantId(request.tenantId());
                                    fresh.setCompanyId(request.companyId());
                                    fresh.setJurisdiction(request.jurisdiction());
                                    fresh.setCode(request.code());
                                    fresh.setPeriodMonth(request.periodMonth());
                                    fresh.setStatus(StatutoryChallanStatus.DRAFT);
                                    return challans.save(fresh);
                                });
        if (c.getStatus() != StatutoryChallanStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Challan is " + c.getStatus() + "; only DRAFT challans accept new deductions");
        }

        List<StatutoryDeduction> unattached =
                deductions.findUnattachedForScope(
                        request.tenantId(),
                        request.companyId(),
                        request.jurisdiction(),
                        request.code(),
                        request.periodMonth());

        BigDecimal totalBase = c.getTotalTaxableBase();
        BigDecimal totalEmp = c.getTotalEmployeeContribution();
        BigDecimal totalEmpr = c.getTotalEmployerContribution();
        BigDecimal totalAll = c.getTotalAmount();
        int totalEmployees = c.getTotalEmployees();
        Set<UUID> employeeIds = new HashSet<>();
        String currency = c.getCurrency();

        for (StatutoryDeduction d : unattached) {
            d.setStatutoryChallan(c);
            totalBase = totalBase.add(d.getTaxableBase());
            totalEmp = totalEmp.add(d.getEmployeeContribution());
            totalEmpr = totalEmpr.add(d.getEmployerContribution());
            totalAll = totalAll.add(d.getTotalAmount());
            employeeIds.add(d.getEmployee().getId());
            if (currency == null || "USD".equals(currency)) {
                currency = d.getCurrency();
            }
        }
        c.setTotalTaxableBase(totalBase);
        c.setTotalEmployeeContribution(totalEmp);
        c.setTotalEmployerContribution(totalEmpr);
        c.setTotalAmount(totalAll);
        c.setTotalEmployees(totalEmployees + employeeIds.size());
        if (currency != null) {
            c.setCurrency(currency);
        }
        return mapper.toResponse(c);
    }

    public StatutoryChallanResponse file(UUID tenantId, UUID id, String filingReference) {
        StatutoryChallan c = require(tenantId, id);
        if (c.getStatus() != StatutoryChallanStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only DRAFT challans can be filed");
        }
        UUID actor = requireActor();
        c.setStatus(StatutoryChallanStatus.FILED);
        c.setFiledAt(Instant.now());
        c.setFiledBy(actor);
        c.setFilingReference(filingReference);
        return mapper.toResponse(c);
    }

    public StatutoryChallanResponse pay(UUID tenantId, UUID id, String paymentReference) {
        StatutoryChallan c = require(tenantId, id);
        if (c.getStatus() != StatutoryChallanStatus.FILED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only FILED challans can be paid");
        }
        UUID actor = requireActor();
        c.setStatus(StatutoryChallanStatus.PAID);
        c.setPaidAt(Instant.now());
        c.setPaidBy(actor);
        c.setPaymentReference(paymentReference);
        return mapper.toResponse(c);
    }

    public StatutoryChallanResponse cancel(UUID tenantId, UUID id) {
        StatutoryChallan c = require(tenantId, id);
        if (c.getStatus() == StatutoryChallanStatus.PAID) {
            throw new ApiException(HttpStatus.CONFLICT, "Paid challans cannot be cancelled");
        }
        c.setStatus(StatutoryChallanStatus.CANCELLED);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public StatutoryChallanResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<StatutoryChallanResponse> forMonth(UUID tenantId, UUID companyId, int periodMonth) {
        return challans
                .findAllByTenantIdAndCompanyIdAndPeriodMonthOrderByCodeAsc(
                        tenantId, companyId, periodMonth)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatutoryChallanResponse> byStatus(
            UUID tenantId, UUID companyId, StatutoryChallanStatus status) {
        return challans
                .findAllByTenantIdAndCompanyIdAndStatusOrderByPeriodMonthDesc(
                        tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private StatutoryChallan require(UUID tenantId, UUID id) {
        return challans.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Statutory challan not found"));
    }

    private static UUID requireActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new ApiException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action", e);
        }
    }
}
