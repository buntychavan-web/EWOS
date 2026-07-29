package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.domain.PayComponentCalculationType;
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
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Statutory extraction from finalized payslips: only deduction lines with a recognised code become
 * statutory rows, extraction is idempotent per (payslip, code), and the period-month key is derived
 * from the payslip's period start.
 */
@ExtendWith(MockitoExtension.class)
class StatutoryDeductionServiceTest {

    @Mock StatutoryDeductionRepository repository;
    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock ClientAccessGuard guard;

    private StatutoryDeductionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new StatutoryDeductionService(
                        repository,
                        runs,
                        payslips,
                        new StatutoryClassifier(),
                        new PayrollMapper(),
                        guard);
    }

    private PayrollRun runIn(UUID company) {
        PayrollRun r = new PayrollRun();
        r.setId(runId);
        r.setCompanyId(company);
        return r;
    }

    private PayslipLine line(PayComponentKind kind, String code, String amount) {
        PayslipLine l = new PayslipLine();
        l.setComponentCodeSnapshot(code);
        l.setKind(kind);
        l.setCalculationType(PayComponentCalculationType.FIXED);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private Payslip payslip() {
        Payslip p = new Payslip();
        p.setId(UUID.randomUUID());
        p.setCompanyId(companyId);
        p.setPeriodStart(LocalDate.of(2026, 3, 1));
        p.setBasicEffective(new BigDecimal("50000"));
        p.setCurrency("INR");
        return p;
    }

    @Test
    void extractForRunThrowsNotFoundForAnUnknownRun() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extractForRun(tenantId, runId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void extractForRunChecksAccessForTheRunsCompany() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of());

        service.extractForRun(tenantId, runId);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void extractForRunIgnoresEarningLinesEvenIfTheyMatchAStatutoryCode() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.EARNING, "PF", "1000"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        int inserted = service.extractForRun(tenantId, runId);

        assertThat(inserted).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void extractForRunIgnoresDeductionsWithAnUnrecognisedCode() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "LOAN_REPAY", "2000"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        int inserted = service.extractForRun(tenantId, runId);

        assertThat(inserted).isZero();
    }

    @Test
    void extractForRunMaterialisesOneRowPerRecognisedStatutoryDeduction() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "PF", "1800"));
        slip.addLine(line(PayComponentKind.DEDUCTION, "PROFESSIONAL_TAX", "200"));
        slip.addLine(line(PayComponentKind.DEDUCTION, "LOAN_REPAY", "2000"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        int inserted = service.extractForRun(tenantId, runId);

        assertThat(inserted).isEqualTo(2);
        verify(repository, times(2)).save(any(StatutoryDeduction.class));
    }

    @Test
    void extractForRunDerivesJurisdictionAndPeriodMonthFromTheClassifierAndPayslip() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "PF", "1800"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        service.extractForRun(tenantId, runId);

        org.mockito.ArgumentCaptor<StatutoryDeduction> captor =
                org.mockito.ArgumentCaptor.forClass(StatutoryDeduction.class);
        verify(repository).save(captor.capture());
        StatutoryDeduction saved = captor.getValue();
        assertThat(saved.getJurisdiction()).isEqualTo("IN");
        assertThat(saved.getCode()).isEqualTo("PF");
        assertThat(saved.getPeriodMonth()).isEqualTo(202603);
        assertThat(saved.getEmployeeContribution()).isEqualByComparingTo("1800");
        assertThat(saved.getEmployerContribution()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1800");
    }

    @Test
    void extractForRunClassifiesEsiAsIndianEsi() {
        // Sprint 16 certification flagged ESI as covered only via the shared PF/PT code path, with
        // no test asserting it by name. This closes that gap the same way PF is certified above.
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "ESI", "750"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        service.extractForRun(tenantId, runId);

        org.mockito.ArgumentCaptor<StatutoryDeduction> captor =
                org.mockito.ArgumentCaptor.forClass(StatutoryDeduction.class);
        verify(repository).save(captor.capture());
        StatutoryDeduction saved = captor.getValue();
        assertThat(saved.getJurisdiction()).isEqualTo("IN");
        assertThat(saved.getCode()).isEqualTo("ESI");
        assertThat(saved.getEmployeeContribution()).isEqualByComparingTo("750");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("750");
    }

    @Test
    void extractForRunClassifiesTdsAsIndianTds() {
        // Same Sprint 16 gap as ESI above, for TDS.
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "TDS", "3200"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        service.extractForRun(tenantId, runId);

        org.mockito.ArgumentCaptor<StatutoryDeduction> captor =
                org.mockito.ArgumentCaptor.forClass(StatutoryDeduction.class);
        verify(repository).save(captor.capture());
        StatutoryDeduction saved = captor.getValue();
        assertThat(saved.getJurisdiction()).isEqualTo("IN");
        assertThat(saved.getCode()).isEqualTo("TDS");
        assertThat(saved.getEmployeeContribution()).isEqualByComparingTo("3200");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("3200");
    }

    @Test
    void extractForRunClassifiesProfessionalTaxAsIndianPt() {
        // PT was exercised (mixed in with PF) by
        // extractForRunMaterialisesOneRowPerRecognisedStatutoryDeduction
        // above but never asserted by name on its own — added for symmetry with the PF/ESI/TDS
        // certification tests.
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "PROFESSIONAL_TAX", "200"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        service.extractForRun(tenantId, runId);

        org.mockito.ArgumentCaptor<StatutoryDeduction> captor =
                org.mockito.ArgumentCaptor.forClass(StatutoryDeduction.class);
        verify(repository).save(captor.capture());
        StatutoryDeduction saved = captor.getValue();
        assertThat(saved.getJurisdiction()).isEqualTo("IN");
        assertThat(saved.getCode()).isEqualTo("PT");
        assertThat(saved.getEmployeeContribution()).isEqualByComparingTo("200");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("200");
    }

    @Test
    void extractForRunIsIdempotentAndSkipsCodesAlreadyExtractedForThatPayslip() {
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "PF", "1800"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        StatutoryDeduction existing = new StatutoryDeduction();
        existing.setCode("PF");
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of(existing));

        int inserted = service.extractForRun(tenantId, runId);

        assertThat(inserted).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void extractForRunTreatsPfAndProvidentFundCodesAsTheSameStatutoryObligation() {
        // Two differently-coded PF components must not double-count against the same payslip: the
        // classifier maps both to the same statutory code, so the idempotency check keys off that.
        when(runs.findByIdAndTenantId(runId, tenantId)).thenReturn(Optional.of(runIn(companyId)));
        Payslip slip = payslip();
        slip.addLine(line(PayComponentKind.DEDUCTION, "PF", "1800"));
        slip.addLine(line(PayComponentKind.DEDUCTION, "PROVIDENT_FUND", "500"));
        when(payslips.findAllForRun(tenantId, runId)).thenReturn(List.of(slip));
        when(repository.findAllForPayslip(tenantId, slip.getId())).thenReturn(List.of());

        int inserted = service.extractForRun(tenantId, runId);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownDeduction() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forPayslipChecksAccessAcrossEveryDistinctCompanyReturned() {
        UUID payslipId = UUID.randomUUID();
        StatutoryDeduction d = new StatutoryDeduction();
        d.setCompanyId(companyId);
        when(repository.findAllForPayslip(tenantId, payslipId)).thenReturn(List.of(d));

        service.forPayslip(tenantId, payslipId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void forEmployeeMonthChecksAccessAcrossEveryDistinctCompanyReturned() {
        UUID employeeId = UUID.randomUUID();
        StatutoryDeduction d = new StatutoryDeduction();
        d.setCompanyId(companyId);
        when(repository.findForEmployeeMonth(tenantId, employeeId, 202603)).thenReturn(List.of(d));

        service.forEmployeeMonth(tenantId, employeeId, 202603);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
