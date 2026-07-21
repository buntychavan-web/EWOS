package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.GeneratePayrollJournalRequest;
import com.ewos.payroll.api.dto.JournalReconciliationResponse;
import com.ewos.payroll.api.dto.PayrollJournalLineResponse;
import com.ewos.payroll.api.dto.PayrollJournalResponse;
import com.ewos.payroll.domain.PayrollJournal;
import com.ewos.payroll.domain.PayrollJournalCsvExporter;
import com.ewos.payroll.domain.PayrollJournalGenerator;
import com.ewos.payroll.domain.PayrollJournalLine;
import com.ewos.payroll.domain.PayrollJournalStatus;
import com.ewos.payroll.domain.PayrollJournalType;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayrollJournalRepository;
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
 * Orchestrates payroll journals — generate from a finalized run, approve, post, export, and
 * reconcile against the run totals.
 */
@Service
@Transactional
public class PayrollJournalService {

    private final PayrollJournalRepository journals;
    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollJournalGenerator generator;
    private final PayrollJournalCsvExporter csv;

    public PayrollJournalService(
            PayrollJournalRepository journals,
            PayrollRunRepository runs,
            PayslipRepository payslips,
            PayrollJournalGenerator generator,
            PayrollJournalCsvExporter csv) {
        this.journals = journals;
        this.runs = runs;
        this.payslips = payslips;
        this.generator = generator;
        this.csv = csv;
    }

    public PayrollJournalResponse generate(GeneratePayrollJournalRequest request) {
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
                    "Journal can only be generated for FINALIZED / FROZEN runs (current: "
                            + run.getStatus()
                            + ")");
        }
        if (journals.existsByTenantIdAndCompanyIdAndJournalNumberIgnoreCase(
                request.tenantId(), request.companyId(), request.journalNumber())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Journal number already in use for this company");
        }
        List<Payslip> slips = payslips.findAllForRun(request.tenantId(), run.getId());
        if (slips.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Run has no payslips to book");
        }
        String currency = slips.get(0).getCurrency();

        PayrollJournalGenerator.GenerationResult result =
                generator.generate(request.tenantId(), request.companyId(), slips, currency);

        PayrollJournal j = new PayrollJournal();
        j.setTenantId(request.tenantId());
        j.setCompanyId(request.companyId());
        j.setPayrollRun(run);
        j.setJournalNumber(request.journalNumber());
        j.setJournalDate(request.journalDate());
        j.setJournalType(
                request.journalType() != null ? request.journalType() : PayrollJournalType.PAYROLL);
        j.setCurrency(currency);
        j.setNotes(request.notes());
        j.setTotalDebit(result.totalDebit());
        j.setTotalCredit(result.totalCredit());
        j.setStatus(PayrollJournalStatus.DRAFT);
        PayrollJournal saved = journals.save(j);
        for (PayrollJournalLine line : result.lines()) {
            saved.addLine(line);
        }
        return toResponse(saved);
    }

    public PayrollJournalResponse approve(UUID tenantId, UUID id) {
        PayrollJournal j = require(tenantId, id);
        if (j.getStatus() != PayrollJournalStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only DRAFT journals can be approved");
        }
        if (j.getTotalDebit().compareTo(j.getTotalCredit()) != 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Journal is out of balance: debit="
                            + j.getTotalDebit()
                            + " credit="
                            + j.getTotalCredit());
        }
        UUID actor = requireActor();
        j.setStatus(PayrollJournalStatus.APPROVED);
        j.setApprovedAt(Instant.now());
        j.setApprovedBy(actor);
        return toResponse(j);
    }

    public PayrollJournalResponse post(UUID tenantId, UUID id) {
        PayrollJournal j = require(tenantId, id);
        if (j.getStatus() != PayrollJournalStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only APPROVED journals can be posted");
        }
        UUID actor = requireActor();
        j.setStatus(PayrollJournalStatus.POSTED);
        j.setPostedAt(Instant.now());
        j.setPostedBy(actor);
        return toResponse(j);
    }

    public PayrollJournalResponse cancel(UUID tenantId, UUID id) {
        PayrollJournal j = require(tenantId, id);
        if (j.getStatus() == PayrollJournalStatus.POSTED
                || j.getStatus() == PayrollJournalStatus.EXPORTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Posted/exported journals cannot be cancelled");
        }
        j.setStatus(PayrollJournalStatus.CANCELLED);
        return toResponse(j);
    }

    public PayrollJournalResponse recordExport(
            UUID tenantId, UUID id, String format, String reference) {
        PayrollJournal j = require(tenantId, id);
        if (j.getStatus() != PayrollJournalStatus.POSTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only POSTED journals can be exported (current: " + j.getStatus() + ")");
        }
        UUID actor = requireActor();
        j.setStatus(PayrollJournalStatus.EXPORTED);
        j.setExportedAt(Instant.now());
        j.setExportedBy(actor);
        j.setExportFormat(format);
        j.setExportReference(reference);
        return toResponse(j);
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId, UUID id) {
        return csv.export(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public JournalReconciliationResponse reconcile(UUID tenantId, UUID id) {
        PayrollJournal j = require(tenantId, id);
        PayrollRun run = j.getPayrollRun();
        BigDecimal expenseDebit = BigDecimal.ZERO;
        for (PayrollJournalLine line : j.getLines()) {
            if (line.getAccountTypeSnapshot() == com.ewos.payroll.domain.GLAccountType.EXPENSE) {
                expenseDebit = expenseDebit.add(line.getDebitAmount());
            }
        }
        BigDecimal delta = expenseDebit.subtract(run.getTotalGross());
        boolean balanced = j.getTotalDebit().compareTo(j.getTotalCredit()) == 0;
        return new JournalReconciliationResponse(
                j.getId(),
                run.getId(),
                run.getTotalGross(),
                run.getTotalDeductions(),
                run.getTotalNet(),
                j.getTotalDebit(),
                j.getTotalCredit(),
                balanced,
                j.getTotalDebit().subtract(j.getTotalCredit()),
                delta);
    }

    @Transactional(readOnly = true)
    public PayrollJournalResponse getById(UUID tenantId, UUID id) {
        return toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayrollJournalResponse> forRun(UUID tenantId, UUID runId) {
        return journals.findAllForRun(tenantId, runId).stream()
                .map(PayrollJournalService::toResponse)
                .toList();
    }

    private PayrollJournal require(UUID tenantId, UUID id) {
        return journals.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Payroll journal not found"));
    }

    static PayrollJournalResponse toResponse(PayrollJournal j) {
        List<PayrollJournalLineResponse> lines =
                j.getLines().stream().map(PayrollJournalService::toResponse).toList();
        return new PayrollJournalResponse(
                j.getId(),
                j.getTenantId(),
                j.getCompanyId(),
                j.getPayrollRun() != null ? j.getPayrollRun().getId() : null,
                j.getJournalNumber(),
                j.getJournalDate(),
                j.getJournalType(),
                j.getStatus(),
                j.getCurrency(),
                j.getTotalDebit(),
                j.getTotalCredit(),
                j.getApprovedAt(),
                j.getApprovedBy(),
                j.getPostedAt(),
                j.getPostedBy(),
                j.getExportedAt(),
                j.getExportedBy(),
                j.getExportFormat(),
                j.getExportReference(),
                j.getNotes(),
                lines,
                j.getVersionNo());
    }

    static PayrollJournalLineResponse toResponse(PayrollJournalLine l) {
        return new PayrollJournalLineResponse(
                l.getId(),
                l.getLineNo(),
                l.getGlAccountCode(),
                l.getGlAccountNameSnapshot(),
                l.getAccountTypeSnapshot(),
                l.getCostCentreCode(),
                l.getBusinessUnitCode(),
                l.getDepartmentCode(),
                l.getSourceKind(),
                l.getSourceReference(),
                l.getDebitAmount(),
                l.getCreditAmount(),
                l.getCurrency(),
                l.getDescription());
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
