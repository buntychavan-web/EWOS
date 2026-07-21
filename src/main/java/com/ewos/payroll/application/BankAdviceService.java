package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.BankAdviceResponse;
import com.ewos.payroll.api.dto.GenerateBankAdviceRequest;
import com.ewos.payroll.domain.BankAdvice;
import com.ewos.payroll.domain.BankAdviceCsvExporter;
import com.ewos.payroll.domain.BankAdviceStatus;
import com.ewos.payroll.domain.EmployeeBankAccount;
import com.ewos.payroll.domain.PaymentInstruction;
import com.ewos.payroll.domain.PaymentInstructionStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.BankAdviceRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeBankAccountRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
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
 * Bank advice orchestration.
 *
 * <ul>
 *   <li>{@link #generate(GenerateBankAdviceRequest)} — creates one advice per finalized payroll
 *       run. Every payslip becomes a {@link PaymentInstruction}; instructions for employees without
 *       a primary bank account or with zero net are marked {@code SKIPPED} up front.
 *   <li>{@link #export(UUID, UUID)} — renders the advice as bytes in the configured format
 *       (currently CSV; NACHA/NEFT/SEPA reserved for future writers).
 *   <li>{@link #markPaid} / {@link #markFailed} — per-instruction settlement callbacks; when the
 *       last pending instruction resolves, the advice transitions to SETTLED.
 * </ul>
 */
@Service
@Transactional
public class BankAdviceService {

    private final BankAdviceRepository advices;
    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmployeeBankAccountRepository bankAccounts;
    private final BankAdviceCsvExporter csv;
    private final PayrollMapper mapper;

    public BankAdviceService(
            BankAdviceRepository advices,
            PayrollRunRepository runs,
            PayslipRepository payslips,
            EmployeeBankAccountRepository bankAccounts,
            BankAdviceCsvExporter csv,
            PayrollMapper mapper) {
        this.advices = advices;
        this.runs = runs;
        this.payslips = payslips;
        this.bankAccounts = bankAccounts;
        this.csv = csv;
        this.mapper = mapper;
    }

    public BankAdviceResponse generate(GenerateBankAdviceRequest request) {
        PayrollRun run =
                runs.findByIdAndTenantId(request.payrollRunId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Payroll run not found"));
        if (!run.getCompanyId().equals(request.companyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Run belongs to a different company");
        }
        if (run.getStatus() != PayrollRunStatus.FINALIZED
                && run.getStatus() != PayrollRunStatus.FROZEN) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Bank advice can only be generated for FINALIZED or FROZEN runs (current: "
                            + run.getStatus()
                            + ")");
        }
        if (advices.existsByTenantIdAndCompanyIdAndAdviceNumberIgnoreCase(
                request.tenantId(), request.companyId(), request.adviceNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Advice number already in use for this company");
        }
        UUID actor = requireActor();

        BankAdvice advice = new BankAdvice();
        advice.setTenantId(request.tenantId());
        advice.setCompanyId(request.companyId());
        advice.setPayrollRun(run);
        advice.setAdviceNumber(request.adviceNumber());
        advice.setAdviceDate(request.adviceDate());
        advice.setFileFormat(request.fileFormat());
        advice.setNotes(request.notes());
        BankAdvice saved = advices.save(advice);

        List<Payslip> slips = payslips.findAllForRun(request.tenantId(), run.getId());
        int total = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        String currency = null;
        for (Payslip slip : slips) {
            PaymentInstruction p = buildInstruction(slip);
            saved.addInstruction(p);
            if (p.getStatus() == PaymentInstructionStatus.PENDING) {
                total++;
                totalAmount = totalAmount.add(p.getAmount());
                if (currency == null) {
                    currency = p.getCurrency();
                }
            }
        }
        saved.setCurrency(currency != null ? currency : "USD");
        saved.setTotalCount(total);
        saved.setTotalAmount(totalAmount);
        saved.setStatus(BankAdviceStatus.GENERATED);
        saved.setGeneratedAt(Instant.now());
        saved.setGeneratedBy(actor);
        return mapper.toResponse(saved);
    }

    private PaymentInstruction buildInstruction(Payslip slip) {
        PaymentInstruction p = new PaymentInstruction();
        p.setPayslip(slip);
        p.setEmployee(slip.getEmployee());
        p.setAmount(slip.getNetAmount());
        p.setCurrency(slip.getCurrency());

        if (slip.getNetAmount() == null || slip.getNetAmount().signum() <= 0) {
            p.setStatus(PaymentInstructionStatus.SKIPPED);
            p.setFailureReason("Net amount is zero");
            p.setBankNameSnapshot("");
            p.setAccountHolderSnapshot(slip.getEmployeeNameSnapshot());
            p.setAccountNumberMasked("");
            return p;
        }

        var primary =
                bankAccounts.findPrimaryForEmployee(slip.getTenantId(), slip.getEmployee().getId());
        if (primary.isEmpty()) {
            p.setStatus(PaymentInstructionStatus.SKIPPED);
            p.setFailureReason("Employee has no primary bank account");
            p.setBankNameSnapshot("");
            p.setAccountHolderSnapshot(slip.getEmployeeNameSnapshot());
            p.setAccountNumberMasked("");
            return p;
        }
        EmployeeBankAccount b = primary.get();
        p.setEmployeeBankAccount(b);
        p.setBankNameSnapshot(b.getBankName());
        p.setAccountHolderSnapshot(b.getAccountHolderName());
        p.setAccountNumberMasked(b.getAccountNumberMasked());
        p.setRoutingCodeSnapshot(b.getRoutingCode());
        p.setSwiftBicSnapshot(b.getSwiftBic());
        p.setStatus(PaymentInstructionStatus.PENDING);
        return p;
    }

    @Transactional(readOnly = true)
    public String export(UUID tenantId, UUID adviceId) {
        BankAdvice advice = require(tenantId, adviceId);
        return csv.export(advice, advice.getInstructions());
    }

    public BankAdviceResponse acknowledge(UUID tenantId, UUID id) {
        BankAdvice a = require(tenantId, id);
        if (a.getStatus() != BankAdviceStatus.GENERATED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only GENERATED advices can be acknowledged; current status is "
                            + a.getStatus());
        }
        UUID actor = requireActor();
        a.setStatus(BankAdviceStatus.ACKNOWLEDGED);
        a.setAcknowledgedAt(Instant.now());
        a.setAcknowledgedBy(actor);
        return mapper.toResponse(a);
    }

    public BankAdviceResponse markFailed(UUID tenantId, UUID id, String reason) {
        BankAdvice a = require(tenantId, id);
        if (a.getStatus() == BankAdviceStatus.SETTLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Settled advices cannot be failed");
        }
        a.setStatus(BankAdviceStatus.FAILED);
        a.setNotes((a.getNotes() == null ? "" : a.getNotes() + "\n") + "FAILED: " + reason);
        return mapper.toResponse(a);
    }

    public BankAdviceResponse markInstructionPaid(
            UUID tenantId, UUID adviceId, UUID instructionId, String settlementReference) {
        BankAdvice a = require(tenantId, adviceId);
        PaymentInstruction p = findInstruction(a, instructionId);
        if (p.getStatus() != PaymentInstructionStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Instruction is " + p.getStatus() + "; cannot mark PAID");
        }
        p.setStatus(PaymentInstructionStatus.PAID);
        p.setSettledAt(Instant.now());
        p.setSettlementReference(settlementReference);
        maybeSettle(a);
        return mapper.toResponse(a);
    }

    public BankAdviceResponse markInstructionFailed(
            UUID tenantId, UUID adviceId, UUID instructionId, String reason) {
        BankAdvice a = require(tenantId, adviceId);
        PaymentInstruction p = findInstruction(a, instructionId);
        if (p.getStatus() != PaymentInstructionStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Instruction is " + p.getStatus() + "; cannot mark FAILED");
        }
        p.setStatus(PaymentInstructionStatus.FAILED);
        p.setFailureReason(reason);
        maybeSettle(a);
        return mapper.toResponse(a);
    }

    private void maybeSettle(BankAdvice a) {
        boolean anyPending =
                a.getInstructions().stream()
                        .anyMatch(i -> i.getStatus() == PaymentInstructionStatus.PENDING);
        if (!anyPending && a.getStatus() != BankAdviceStatus.FAILED) {
            a.setStatus(BankAdviceStatus.SETTLED);
            a.setSettledAt(Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public BankAdviceResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<BankAdviceResponse> forRun(UUID tenantId, UUID runId) {
        return advices.findAllForRun(tenantId, runId).stream().map(mapper::toResponse).toList();
    }

    private PaymentInstruction findInstruction(BankAdvice a, UUID instructionId) {
        return a.getInstructions().stream()
                .filter(p -> p.getId().equals(instructionId))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Instruction not found on this advice"));
    }

    private BankAdvice require(UUID tenantId, UUID id) {
        return advices.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Bank advice not found"));
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
