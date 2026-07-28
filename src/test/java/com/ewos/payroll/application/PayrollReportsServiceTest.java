package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.domain.GLAccountType;
import com.ewos.payroll.domain.PayrollJournal;
import com.ewos.payroll.domain.PayrollJournalLine;
import com.ewos.payroll.domain.PayrollJournalLineSourceKind;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunType;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipStatus;
import com.ewos.payroll.infrastructure.persistence.PayrollJournalRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Payroll reporting projections: register totals roll-up, run-type gating on supplementary /
 * F&amp;F registers, cross-company payslip rejection, employee-level variance math (including the
 * joiner/ leaver-only cases where one side of the comparison is absent), and cost-centre
 * debit/credit aggregation from journal lines.
 */
@ExtendWith(MockitoExtension.class)
class PayrollReportsServiceTest {

    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock PayrollJournalRepository journals;
    @Mock ClientAccessGuard guard;

    private PayrollReportsService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollReportsService(runs, payslips, journals, guard);
    }

    private PayrollRun run(PayrollRunType type) {
        PayrollRun r = new PayrollRun();
        r.setId(runId);
        r.setCompanyId(companyId);
        r.setRunType(type);
        return r;
    }

    private Payslip payslip(
            UUID employeeId, BigDecimal gross, BigDecimal deductions, BigDecimal net) {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setCompanyId(companyId);
        p.setGrossAmount(gross);
        p.setDeductionsAmount(deductions);
        p.setNetAmount(net);
        p.setStatus(PayslipStatus.FINALIZED);
        Employee e = new Employee();
        e.setId(employeeId);
        p.setEmployee(e);
        return p;
    }

    @Test
    void salaryRegisterForRunThrowsNotFoundForAnUnknownRun() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salaryRegisterForRun(tenantId, companyId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void salaryRegisterForRunRejectsARunBelongingToAnotherCompany() {
        PayrollRun r = run(PayrollRunType.REGULAR);
        r.setCompanyId(UUID.randomUUID());
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.salaryRegisterForRun(tenantId, companyId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void salaryRegisterForRunSumsTotalsAcrossAllPayslips() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunType.REGULAR)));
        Payslip p1 =
                payslip(
                        UUID.randomUUID(),
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("800"));
        Payslip p2 =
                payslip(
                        UUID.randomUUID(),
                        new BigDecimal("2000"),
                        new BigDecimal("400"),
                        new BigDecimal("1600"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(p1, p2));

        var register = service.salaryRegisterForRun(tenantId, companyId, runId);

        assertThat(register.reportCode()).isEqualTo("SALARY_REGISTER");
        assertThat(register.rowCount()).isEqualTo(2);
        assertThat(register.totalGross()).isEqualByComparingTo("3000");
        assertThat(register.totalDeductions()).isEqualByComparingTo("600");
        assertThat(register.totalNet()).isEqualByComparingTo("2400");
    }

    @Test
    void salaryRegisterForRunRejectsAPayslipFromADifferentCompany() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunType.REGULAR)));
        Payslip foreign =
                payslip(
                        UUID.randomUUID(),
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("800"));
        foreign.setCompanyId(UUID.randomUUID());
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(foreign));

        assertThatThrownBy(() -> service.salaryRegisterForRun(tenantId, companyId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void supplementaryRegisterRejectsANonSupplementaryRun() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunType.REGULAR)));

        assertThatThrownBy(() -> service.supplementaryRegister(tenantId, companyId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void finalSettlementRegisterRejectsANonFinalSettlementRun() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunType.REGULAR)));

        assertThatThrownBy(() -> service.finalSettlementRegister(tenantId, companyId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void netVarianceComputesDeltaAndPercentForAnEmployeeInBothRuns() {
        UUID empId = UUID.randomUUID();
        UUID currentRunId = UUID.randomUUID();
        UUID previousRunId = UUID.randomUUID();
        Payslip current =
                payslip(
                        empId,
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("880"));
        Payslip previous =
                payslip(
                        empId,
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("800"));
        when(payslips.findAllForRun(tenantId, currentRunId)).thenReturn(List.of(current));
        when(payslips.findAllForRun(tenantId, previousRunId)).thenReturn(List.of(previous));

        var report = service.netVariance(tenantId, companyId, currentRunId, previousRunId);

        assertThat(report.metric()).isEqualTo("NET");
        assertThat(report.headcountCurrent()).isEqualTo(1);
        assertThat(report.headcountPrevious()).isEqualTo(1);
        assertThat(report.headcountDelta()).isZero();
        assertThat(report.rows()).hasSize(1);
        var row = report.rows().get(0);
        assertThat(row.delta()).isEqualByComparingTo("80");
        assertThat(row.deltaPercent()).isEqualByComparingTo("10.0000");
    }

    @Test
    void netVarianceTreatsANewJoinerAsZeroPreviousWithoutDividingByZero() {
        UUID empId = UUID.randomUUID();
        UUID currentRunId = UUID.randomUUID();
        UUID previousRunId = UUID.randomUUID();
        Payslip joiner =
                payslip(
                        empId,
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("800"));
        when(payslips.findAllForRun(tenantId, currentRunId)).thenReturn(List.of(joiner));
        when(payslips.findAllForRun(tenantId, previousRunId)).thenReturn(List.of());

        var report = service.netVariance(tenantId, companyId, currentRunId, previousRunId);

        assertThat(report.headcountDelta()).isEqualTo(1);
        var row = report.rows().get(0);
        assertThat(row.previous()).isEqualByComparingTo("0");
        assertThat(row.deltaPercent()).isEqualByComparingTo("0.0000");
    }

    @Test
    void netVarianceIncludesALeaverPresentOnlyInThePreviousRun() {
        UUID empId = UUID.randomUUID();
        UUID currentRunId = UUID.randomUUID();
        UUID previousRunId = UUID.randomUUID();
        Payslip leaver =
                payslip(
                        empId,
                        new BigDecimal("1000"),
                        new BigDecimal("200"),
                        new BigDecimal("800"));
        when(payslips.findAllForRun(tenantId, currentRunId)).thenReturn(List.of());
        when(payslips.findAllForRun(tenantId, previousRunId)).thenReturn(List.of(leaver));

        var report = service.netVariance(tenantId, companyId, currentRunId, previousRunId);

        assertThat(report.headcountDelta()).isEqualTo(-1);
        var row = report.rows().get(0);
        assertThat(row.current()).isEqualByComparingTo("0");
        assertThat(row.delta()).isEqualByComparingTo("-800");
    }

    @Test
    void costCentreReportAggregatesDebitsAndCreditsByCostCentreAndAccount() {
        PayrollRun r = run(PayrollRunType.REGULAR);
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(r));
        PayrollJournal j = new PayrollJournal();
        PayrollJournalLine l1 =
                journalLine("CC-1", "6000", new BigDecimal("100.00"), BigDecimal.ZERO);
        PayrollJournalLine l2 =
                journalLine("CC-1", "6000", new BigDecimal("50.00"), BigDecimal.ZERO);
        PayrollJournalLine l3 =
                journalLine(null, "2100", BigDecimal.ZERO, new BigDecimal("150.00"));
        j.addLine(l1);
        j.addLine(l2);
        j.addLine(l3);
        when(journals.findAllForRun(tenantId, runId)).thenReturn(List.of(j));

        var rows = service.costCentreReport(tenantId, runId);

        assertThat(rows).hasSize(2);
        var ccRow =
                rows.stream()
                        .filter(row -> row.costCentreCode().equals("CC-1"))
                        .findFirst()
                        .orElseThrow();
        assertThat(ccRow.debitTotal()).isEqualByComparingTo("150.00");
        var unassigned =
                rows.stream()
                        .filter(row -> row.costCentreCode().equals("-"))
                        .findFirst()
                        .orElseThrow();
        assertThat(unassigned.creditTotal()).isEqualByComparingTo("150.00");
    }

    private static PayrollJournalLine journalLine(
            String costCentre, String glAccount, BigDecimal debit, BigDecimal credit) {
        PayrollJournalLine l = new PayrollJournalLine();
        l.setCostCentreCode(costCentre);
        l.setGlAccountCode(glAccount);
        l.setAccountTypeSnapshot(GLAccountType.EXPENSE);
        l.setSourceKind(PayrollJournalLineSourceKind.PAY_COMPONENT);
        l.setDebitAmount(debit);
        l.setCreditAmount(credit);
        return l;
    }

    @Test
    void dashboardChecksCompanyAccess() {
        service.dashboard(tenantId, companyId);

        verify(guard).requireAccessForCompany(companyId);
    }
}
