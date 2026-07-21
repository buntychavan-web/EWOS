package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateFinalSettlementRequest;
import com.ewos.payroll.api.dto.FinalSettlementResponse;
import com.ewos.payroll.api.dto.UpdateFinalSettlementRequest;
import com.ewos.payroll.domain.FinalSettlement;
import com.ewos.payroll.domain.FinalSettlementStatus;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.infrastructure.persistence.FinalSettlementRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full & Final settlement orchestration.
 *
 * <ul>
 *   <li>{@code DRAFT} — freely editable amounts.
 *   <li>{@code APPROVED} — locked; amounts materialised as pending {@link PayrollArrear} rows so
 *       the FINAL_SETTLEMENT run picks them up.
 *   <li>{@code SETTLED} — settlement run generated the payslip; row is immutable.
 * </ul>
 *
 * The settlement run itself is created by {@link PayrollRunService#startFinalSettlement}. Approval
 * only queues the arrears; settlement invokes the run.
 */
@Service
@Transactional
public class FinalSettlementService {

    private final FinalSettlementRepository repository;
    private final EmployeeRepository employees;
    private final PayrollArrearRepository arrears;
    private final PayrollRunService runs;
    private final PayrollMapper mapper;

    public FinalSettlementService(
            FinalSettlementRepository repository,
            EmployeeRepository employees,
            PayrollArrearRepository arrears,
            PayrollRunService runs,
            PayrollMapper mapper) {
        this.repository = repository;
        this.employees = employees;
        this.arrears = arrears;
        this.runs = runs;
        this.mapper = mapper;
    }

    public FinalSettlementResponse create(CreateFinalSettlementRequest request) {
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        if (repository.findLiveForEmployee(request.tenantId(), request.employeeId()).isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A live settlement already exists for this employee");
        }
        FinalSettlement s = new FinalSettlement();
        s.setTenantId(request.tenantId());
        s.setCompanyId(request.companyId());
        s.setEmployee(employee);
        s.setTerminationDate(request.terminationDate());
        s.setLastWorkingDate(request.lastWorkingDate());
        s.setUnusedLeaveDays(zero(request.unusedLeaveDays()));
        s.setEncashmentAmount(zero(request.encashmentAmount()));
        s.setGratuityAmount(zero(request.gratuityAmount()));
        s.setNoticePayRecovery(zero(request.noticePayRecovery()));
        s.setNoticePayReceivable(zero(request.noticePayReceivable()));
        s.setOtherEarnings(zero(request.otherEarnings()));
        s.setOtherDeductions(zero(request.otherDeductions()));
        s.setCurrency(request.currency());
        s.setNotes(request.notes());
        s.setStatus(FinalSettlementStatus.DRAFT);
        return mapper.toResponse(repository.save(s));
    }

    public FinalSettlementResponse update(UUID tenantId, UUID id, UpdateFinalSettlementRequest r) {
        FinalSettlement s = require(tenantId, id);
        if (s.getStatus() != FinalSettlementStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only DRAFT settlements can be edited");
        }
        if (r.terminationDate() != null) {
            s.setTerminationDate(r.terminationDate());
        }
        if (r.lastWorkingDate() != null) {
            s.setLastWorkingDate(r.lastWorkingDate());
        }
        if (r.unusedLeaveDays() != null) {
            s.setUnusedLeaveDays(r.unusedLeaveDays());
        }
        if (r.encashmentAmount() != null) {
            s.setEncashmentAmount(r.encashmentAmount());
        }
        if (r.gratuityAmount() != null) {
            s.setGratuityAmount(r.gratuityAmount());
        }
        if (r.noticePayRecovery() != null) {
            s.setNoticePayRecovery(r.noticePayRecovery());
        }
        if (r.noticePayReceivable() != null) {
            s.setNoticePayReceivable(r.noticePayReceivable());
        }
        if (r.otherEarnings() != null) {
            s.setOtherEarnings(r.otherEarnings());
        }
        if (r.otherDeductions() != null) {
            s.setOtherDeductions(r.otherDeductions());
        }
        if (r.notes() != null) {
            s.setNotes(r.notes());
        }
        return mapper.toResponse(s);
    }

    public FinalSettlementResponse approve(UUID tenantId, UUID id) {
        FinalSettlement s = require(tenantId, id);
        if (s.getStatus() != FinalSettlementStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only DRAFT settlements can be approved");
        }
        UUID actor = requireActor();
        s.setStatus(FinalSettlementStatus.APPROVED);
        s.setApprovedAt(Instant.now());
        s.setApprovedBy(actor);
        queueArrears(s);
        return mapper.toResponse(s);
    }

    public FinalSettlementResponse settle(UUID tenantId, UUID id, UUID payrollPeriodId) {
        FinalSettlement s = require(tenantId, id);
        if (s.getStatus() != FinalSettlementStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only APPROVED settlements can be settled");
        }
        UUID actor = requireActor();
        PayrollRun run =
                runs.startFinalSettlement(
                        tenantId, s.getCompanyId(), payrollPeriodId, s.getEmployee().getId());
        s.setSettlementRun(run);
        s.setStatus(FinalSettlementStatus.SETTLED);
        s.setSettledAt(Instant.now());
        s.setSettledBy(actor);
        return mapper.toResponse(s);
    }

    public FinalSettlementResponse cancel(UUID tenantId, UUID id) {
        FinalSettlement s = require(tenantId, id);
        if (s.getStatus() == FinalSettlementStatus.SETTLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Settled records cannot be cancelled");
        }
        s.setStatus(FinalSettlementStatus.CANCELLED);
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public FinalSettlementResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<FinalSettlementResponse> byStatus(
            UUID tenantId, UUID companyId, FinalSettlementStatus status) {
        return repository
                .findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                        tenantId, companyId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void queueArrears(FinalSettlement s) {
        queue(s, "FFS_ENCASHMENT", s.getEncashmentAmount(), PayComponentKind.EARNING);
        queue(s, "FFS_GRATUITY", s.getGratuityAmount(), PayComponentKind.EARNING);
        queue(s, "FFS_NOTICE_RECEIVABLE", s.getNoticePayReceivable(), PayComponentKind.EARNING);
        queue(s, "FFS_OTHER_EARNINGS", s.getOtherEarnings(), PayComponentKind.EARNING);
        queue(s, "FFS_NOTICE_RECOVERY", s.getNoticePayRecovery(), PayComponentKind.DEDUCTION);
        queue(s, "FFS_OTHER_DEDUCTIONS", s.getOtherDeductions(), PayComponentKind.DEDUCTION);
    }

    private void queue(
            FinalSettlement s, String reasonCode, BigDecimal amount, PayComponentKind kind) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PayrollArrear a = new PayrollArrear();
        a.setTenantId(s.getTenantId());
        a.setCompanyId(s.getCompanyId());
        a.setEmployee(s.getEmployee());
        a.setReasonCode(reasonCode);
        a.setDescription("Full & Final settlement — " + reasonCode);
        a.setAmount(amount);
        a.setKind(kind);
        arrears.save(a);
    }

    public FinalSettlement require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Final settlement not found"));
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
