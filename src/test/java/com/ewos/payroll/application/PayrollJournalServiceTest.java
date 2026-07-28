package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.GeneratePayrollJournalRequest;
import com.ewos.payroll.domain.GLAccountType;
import com.ewos.payroll.domain.PayrollJournal;
import com.ewos.payroll.domain.PayrollJournalCsvExporter;
import com.ewos.payroll.domain.PayrollJournalGenerator;
import com.ewos.payroll.domain.PayrollJournalLine;
import com.ewos.payroll.domain.PayrollJournalLineSourceKind;
import com.ewos.payroll.domain.PayrollJournalStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.infrastructure.persistence.PayrollJournalRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Payroll journal orchestration: run-status/company gating before generation, balance enforcement
 * before approval, the DRAFT-&gt;APPROVED-&gt;POSTED-&gt;EXPORTED lifecycle guards, and
 * reconciliation against the source run's totals.
 */
@ExtendWith(MockitoExtension.class)
class PayrollJournalServiceTest {

    @Mock PayrollJournalRepository journals;
    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock PayrollJournalGenerator generator;
    @Mock ClientAccessGuard guard;
    private final PayrollJournalCsvExporter csv = new PayrollJournalCsvExporter();

    private PayrollJournalService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollJournalService(journals, runs, payslips, generator, csv, guard);
        org.mockito.Mockito.lenient()
                .when(journals.save(any(PayrollJournal.class)))
                .thenAnswer(
                        inv -> {
                            PayrollJournal j = inv.getArgument(0);
                            if (j.getId() == null) {
                                j.setId(UUID.randomUUID());
                            }
                            return j;
                        });
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PayrollRun run(PayrollRunStatus status) {
        PayrollRun r = new PayrollRun();
        r.setId(runId);
        r.setCompanyId(companyId);
        r.setStatus(status);
        r.setTotalGross(new BigDecimal("1000.00"));
        r.setTotalDeductions(new BigDecimal("200.00"));
        r.setTotalNet(new BigDecimal("800.00"));
        return r;
    }

    private Payslip payslip() {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setCurrency("INR");
        p.setNetAmount(new BigDecimal("800.00"));
        return p;
    }

    private GeneratePayrollJournalRequest request(String journalNumber) {
        return new GeneratePayrollJournalRequest(
                tenantId, companyId, runId, journalNumber, LocalDate.of(2026, 4, 1), null, null);
    }

    @Test
    void generateChecksCompanyAccessBeforeLoadingTheRun() {
        service = new PayrollJournalService(journals, runs, payslips, generator, csv, guard);
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(request("JRN-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void generateRejectsARunBelongingToADifferentCompany() {
        PayrollRun r = run(PayrollRunStatus.FINALIZED);
        r.setCompanyId(UUID.randomUUID());
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.generate(request("JRN-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void generateRejectsARunThatIsNotFinalizedOrFrozen() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.COMPLETED)));

        assertThatThrownBy(() -> service.generate(request("JRN-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateRejectsADuplicateJournalNumberForTheCompany() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(journals.existsByTenantIdAndCompanyIdAndJournalNumberIgnoreCase(
                        tenantId, companyId, "JRN-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.generate(request("JRN-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void generateRejectsARunWithNoPayslips() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(journals.existsByTenantIdAndCompanyIdAndJournalNumberIgnoreCase(
                        tenantId, companyId, "JRN-1"))
                .thenReturn(false);
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(request("JRN-1")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void generateBuildsADraftJournalFromTheGeneratorResult() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));
        when(journals.existsByTenantIdAndCompanyIdAndJournalNumberIgnoreCase(
                        tenantId, companyId, "JRN-1"))
                .thenReturn(false);
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(payslip()));
        PayrollJournalLine line = new PayrollJournalLine();
        line.setAccountTypeSnapshot(GLAccountType.EXPENSE);
        line.setSourceKind(PayrollJournalLineSourceKind.PAY_COMPONENT);
        line.setDebitAmount(new BigDecimal("800.0000"));
        line.setCreditAmount(BigDecimal.ZERO);
        when(generator.generate(eq(tenantId), eq(companyId), any(), eq("INR")))
                .thenReturn(
                        new PayrollJournalGenerator.GenerationResult(
                                List.of(line),
                                new BigDecimal("800.0000"),
                                new BigDecimal("800.0000")));

        var response = service.generate(request("JRN-1"));

        assertThat(response.status()).isEqualTo(PayrollJournalStatus.DRAFT);
        assertThat(response.currency()).isEqualTo("INR");
        assertThat(response.totalDebit()).isEqualByComparingTo("800.0000");
        assertThat(response.lines()).hasSize(1);
    }

    @Test
    void approveRejectsAJournalThatIsNotDraft() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.POSTED);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.approve(tenantId, j.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approveRejectsAnOutOfBalanceJournal() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.DRAFT);
        j.setTotalDebit(new BigDecimal("100.00"));
        j.setTotalCredit(new BigDecimal("90.00"));
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.approve(tenantId, j.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void approveStampsTheAuthenticatedActorAndTimestamp() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.DRAFT);
        j.setTotalDebit(new BigDecimal("100.00"));
        j.setTotalCredit(new BigDecimal("100.00"));
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        var response = service.approve(tenantId, j.getId());

        assertThat(response.status()).isEqualTo(PayrollJournalStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo(actorId);
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    void postRejectsAJournalThatIsNotApproved() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.DRAFT);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.post(tenantId, j.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelRejectsAPostedOrExportedJournal() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.EXPORTED);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.cancel(tenantId, j.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordExportRejectsAJournalThatIsNotPosted() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.APPROVED);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        assertThatThrownBy(() -> service.recordExport(tenantId, j.getId(), "CSV", "ref-1"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(journals, never()).save(any());
    }

    @Test
    void recordExportStampsFormatAndReferenceWhenPosted() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setStatus(PayrollJournalStatus.POSTED);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        var response = service.recordExport(tenantId, j.getId(), "CSV", "ref-1");

        assertThat(response.status()).isEqualTo(PayrollJournalStatus.EXPORTED);
        assertThat(response.exportFormat()).isEqualTo("CSV");
        assertThat(response.exportReference()).isEqualTo("ref-1");
        assertThat(response.exportedBy()).isEqualTo(actorId);
    }

    @Test
    void reconcileFlagsAnUnbalancedJournalAndComputesTheExpenseDelta() {
        PayrollRun r = run(PayrollRunStatus.FINALIZED);
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setPayrollRun(r);
        j.setTotalDebit(new BigDecimal("900.00"));
        j.setTotalCredit(new BigDecimal("900.00"));
        PayrollJournalLine expenseLine = new PayrollJournalLine();
        expenseLine.setAccountTypeSnapshot(GLAccountType.EXPENSE);
        expenseLine.setDebitAmount(new BigDecimal("900.00"));
        expenseLine.setCreditAmount(BigDecimal.ZERO);
        j.addLine(expenseLine);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        var response = service.reconcile(tenantId, j.getId());

        assertThat(response.balanced()).isTrue();
        assertThat(response.runGross()).isEqualByComparingTo("1000.00");
        assertThat(response.expenseVsRunGrossDelta()).isEqualByComparingTo("-100.00");
        assertThat(response.debitVsCreditDelta()).isEqualByComparingTo("0.00");
    }

    @Test
    void forRunChecksAccessForEveryDistinctCompanyAmongTheResults() {
        PayrollJournal j = new PayrollJournal();
        j.setCompanyId(companyId);
        when(journals.findAllForRun(tenantId, runId)).thenReturn(List.of(j));

        service.forRun(tenantId, runId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownJournal() {
        UUID id = UUID.randomUUID();
        when(journals.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void exportCsvDelegatesToTheCsvExporter() {
        PayrollJournal j = new PayrollJournal();
        j.setId(UUID.randomUUID());
        j.setCompanyId(companyId);
        j.setJournalNumber("JRN-1");
        j.setJournalDate(LocalDate.of(2026, 4, 1));
        PayrollJournalLine line = new PayrollJournalLine();
        line.setAccountTypeSnapshot(GLAccountType.EXPENSE);
        line.setSourceKind(PayrollJournalLineSourceKind.PAY_COMPONENT);
        line.setDebitAmount(new BigDecimal("100.00"));
        line.setCreditAmount(BigDecimal.ZERO);
        j.addLine(line);
        when(journals.findByIdAndTenantId(j.getId(), tenantId)).thenReturn(Optional.of(j));

        String out = service.exportCsv(tenantId, j.getId());

        assertThat(out).startsWith("journal_number,journal_date");
        assertThat(out).contains("JRN-1");
    }
}
