package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.PayrollRunTimelineEventResponse;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.payroll.domain.TdsAdjustmentLog;
import com.ewos.payroll.domain.TdsAdjustmentType;
import com.ewos.payroll.infrastructure.persistence.EmployeeEsiEnrollmentRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.payroll.infrastructure.persistence.TdsAdjustmentLogRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 14.2 — the central Payroll orchestration service. Verifies the Chinese Wall guard runs
 * before any run-processing work begins, and that id-based lookups resolve the company from the
 * loaded run before checking access.
 */
@ExtendWith(MockitoExtension.class)
class PayrollRunServiceTest {

    @Mock PayrollRunRepository runs;
    @Mock PayslipRepository payslips;
    @Mock PayrollPeriodService periods;
    @Mock EmployeeCompensationService compensations;
    @Mock PayrollCalculator calculator;
    @Mock LopCalculator lop;
    @Mock PayrollArrearRepository arrears;
    @Mock LeaveRequestRepository leaves;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;
    @Mock StatutoryConfigResolver statutoryConfigResolver;
    @Mock PfCalculationService pfCalculationService;
    @Mock EsiCalculationService esiCalculationService;
    @Mock ProfessionalTaxCalculationService professionalTaxCalculationService;
    @Mock LwfCalculationService lwfCalculationService;
    @Mock IncomeTaxCalculationService incomeTaxCalculationService;
    @Mock EmployeePayrollProfileRepository payrollProfiles;
    @Mock EmployeeEsiEnrollmentRepository esiEnrollments;
    @Mock EmployeeTaxDeclarationRepository taxDeclarations;
    @Mock PayrollValidator validator;
    @Mock TdsAdjustmentLogRepository tdsAdjustmentLogs;

    private PayrollRunService service;

    @BeforeEach
    void setUp() {
        service =
                new PayrollRunService(
                        runs,
                        payslips,
                        periods,
                        compensations,
                        calculator,
                        lop,
                        arrears,
                        leaves,
                        new PayrollPolicy(),
                        new PayrollMapper(),
                        events,
                        guard,
                        statutoryConfigResolver,
                        pfCalculationService,
                        esiCalculationService,
                        professionalTaxCalculationService,
                        lwfCalculationService,
                        incomeTaxCalculationService,
                        payrollProfiles,
                        esiEnrollments,
                        taxDeclarations,
                        validator,
                        tdsAdjustmentLogs);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(runs.save(any(PayrollRun.class)))
                .thenAnswer(
                        inv -> {
                            PayrollRun r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
        org.mockito.Mockito.lenient()
                .when(validator.validate(any(), any()))
                .thenReturn(new PayrollValidationReport(List.of(), List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startChecksAccessBeforeLoadingThePeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.start(
                                        new StartPayrollRunRequest(tenantId, companyId, periodId)))
                .isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(periods);
    }

    @Test
    void startProcessesOnceAccessIsGranted() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        when(periods.require(tenantId, periodId)).thenReturn(period);
        when(compensations.activeForCompany(tenantId, companyId)).thenReturn(List.of());
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.ZERO);

        PayrollRunResponse r =
                service.start(new StartPayrollRunRequest(tenantId, companyId, periodId));

        assertThat(r.status()).isEqualTo(PayrollRunStatus.COMPLETED);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void startRecordsTheValidatorsReportOntoTheRunForAudit() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        when(periods.require(tenantId, periodId)).thenReturn(period);
        when(compensations.activeForCompany(tenantId, companyId)).thenReturn(List.of());
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        UUID employeeId = UUID.randomUUID();
        when(validator.validate(any(), any()))
                .thenReturn(
                        new PayrollValidationReport(
                                List.of(),
                                List.of(
                                        new PayrollValidationReport.Issue(
                                                employeeId,
                                                "Jane Doe",
                                                "NO_PAYROLL_PROFILE",
                                                "No active payroll profile"))));

        PayrollRunResponse r =
                service.start(new StartPayrollRunRequest(tenantId, companyId, periodId));

        assertThat(r.validationReportJson()).contains("NO_PAYROLL_PROFILE").contains("Jane Doe");
    }

    @Test
    void startSupplementaryChecksAccessForTheGivenCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.startSupplementary(
                                        tenantId, companyId, periodId, List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class);
        org.mockito.Mockito.verifyNoInteractions(periods);
    }

    @Test
    void getByIdChecksAccessForTheRunsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(companyId);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        service.getById(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forPeriodChecksAccessForEveryDistinctCompanyInTheResultSet() {
        UUID tenantId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        run.setCompanyId(companyId);
        when(runs.findAllForPeriod(tenantId, periodId)).thenReturn(List.of(run));

        List<PayrollRunResponse> results = service.forPeriod(tenantId, periodId);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void forCompanyWithNoStatusListsEveryRunAcrossPeriods() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(UUID.randomUUID());
        run.setCompanyId(companyId);
        when(runs.findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(tenantId, companyId))
                .thenReturn(List.of(run));

        List<PayrollRunResponse> results = service.forCompany(tenantId, companyId, null);

        assertThat(results).hasSize(1);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forCompanyWithStatusFiltersByStatus() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(runs.findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                        tenantId, companyId, PayrollRunStatus.FINALIZED))
                .thenReturn(List.of());

        service.forCompany(tenantId, companyId, PayrollRunStatus.FINALIZED);

        verify(runs)
                .findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                        tenantId, companyId, PayrollRunStatus.FINALIZED);
    }

    @Test
    void timelineOrdersEventsChronologicallyAndOmitsUnreachedStages() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID starter = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(companyId);
        run.setRunType(com.ewos.payroll.domain.PayrollRunType.REGULAR);
        run.setStartedAt(java.time.Instant.parse("2026-04-01T10:00:00Z"));
        run.setStartedBy(starter);
        run.setCompletedAt(java.time.Instant.parse("2026-04-01T10:05:00Z"));
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        List<PayrollRunTimelineEventResponse> events = service.timeline(tenantId, id);

        assertThat(events)
                .extracting(PayrollRunTimelineEventResponse::eventType)
                .containsExactly("CREATED", "STARTED", "COMPLETED");
        assertThat(events.get(1).actor()).isEqualTo(starter);
        verify(guard).requireAccessForCompany(companyId);
    }

    // --- supplementary runs ---

    @Test
    void startSupplementaryRejectsAnEmptyEmployeeList() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> service.startSupplementary(tenantId, companyId, periodId, List.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least one employee");
        org.mockito.Mockito.verifyNoInteractions(periods);
    }

    @Test
    void startSupplementaryRejectsAPeriodBelongingToADifferentCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(UUID.randomUUID());
        when(periods.require(tenantId, periodId)).thenReturn(period);

        assertThatThrownBy(
                        () ->
                                service.startSupplementary(
                                        tenantId, companyId, periodId, List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void startSupplementaryRejectsAClosedPeriod() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.CLOSED);
        when(periods.require(tenantId, periodId)).thenReturn(period);

        assertThatThrownBy(
                        () ->
                                service.startSupplementary(
                                        tenantId, companyId, periodId, List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void startSupplementaryAllowsAnOpenPeriodUnlikeARegularRunWhichRequiresLocked() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.OPEN);
        when(periods.require(tenantId, periodId)).thenReturn(period);
        when(compensations.activeForEmployeeIds(tenantId, List.of(employeeId)))
                .thenReturn(List.of());
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.ZERO);

        PayrollRunResponse r =
                service.startSupplementary(tenantId, companyId, periodId, List.of(employeeId));

        assertThat(r.status()).isEqualTo(PayrollRunStatus.COMPLETED);
        assertThat(r.runType()).isEqualTo(com.ewos.payroll.domain.PayrollRunType.SUPPLEMENTARY);
    }

    @Test
    void startSupplementaryOnlyProcessesTheGivenEmployeesNotTheWholeCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        when(periods.require(tenantId, periodId)).thenReturn(period);
        when(compensations.activeForEmployeeIds(tenantId, List.of(employeeId)))
                .thenReturn(List.of());
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.ZERO);

        service.startSupplementary(tenantId, companyId, periodId, List.of(employeeId));

        verify(compensations).activeForEmployeeIds(tenantId, List.of(employeeId));
        org.mockito.Mockito.verify(compensations, org.mockito.Mockito.never())
                .activeForCompany(any(), any());
    }

    // --- finalizeRun ---

    @Test
    void finalizeRunRejectedWhenTheRunIsNotCompleted() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(UUID.randomUUID());
        run.setStatus(PayrollRunStatus.PENDING);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.finalizeRun(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void finalizeRunFinalizesEveryPayslipOnTheRun() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(companyId);
        run.setStatus(PayrollRunStatus.COMPLETED);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));
        com.ewos.payroll.domain.Payslip slip = new com.ewos.payroll.domain.Payslip();
        slip.setId(UUID.randomUUID());
        slip.setStatus(com.ewos.payroll.domain.PayslipStatus.DRAFT);
        when(payslips.findAllForRun(tenantId, id)).thenReturn(List.of(slip));

        service.finalizeRun(tenantId, id);

        assertThat(slip.getStatus()).isEqualTo(com.ewos.payroll.domain.PayslipStatus.FINALIZED);
        assertThat(run.getStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
        assertThat(run.getFinalizedBy()).isNotNull();
    }

    // --- freeze ---

    @Test
    void freezeRejectedWhenTheRunIsNotFinalized() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(UUID.randomUUID());
        run.setStatus(PayrollRunStatus.COMPLETED);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> service.freeze(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void freezeLocksAFinalizedRun() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollRun run = new PayrollRun();
        run.setId(id);
        run.setCompanyId(companyId);
        run.setStatus(PayrollRunStatus.FINALIZED);
        when(runs.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(run));

        var response = service.freeze(tenantId, id);

        assertThat(response.status()).isEqualTo(PayrollRunStatus.FROZEN);
        assertThat(run.getFrozenBy()).isNotNull();
    }

    // --- exception handling during processing ---

    @Test
    void startMarksTheRunFailedAndRethrowsWhenProcessingThrows() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        when(periods.require(tenantId, periodId)).thenReturn(period);

        com.ewos.employee.domain.Employee employee = new com.ewos.employee.domain.Employee();
        employee.setId(employeeId);
        com.ewos.payroll.domain.EmployeeCompensation comp =
                new com.ewos.payroll.domain.EmployeeCompensation();
        comp.setEmployee(employee);
        when(compensations.activeForCompany(tenantId, companyId)).thenReturn(List.of(comp));
        // Sprint 19 — leave/arrear lookups are bulk-fetched once for the whole run rather than
        // once per employee; this now fails during that bulk fetch instead of mid-loop, but the
        // observable contract under test (processing failure -> run FAILED, exception rethrown)
        // is unchanged.
        when(leaves.findApprovedOverlappingForEmployees(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("leave lookup exploded"));

        assertThatThrownBy(
                        () ->
                                service.start(
                                        new StartPayrollRunRequest(tenantId, companyId, periodId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payroll run failed")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        org.mockito.ArgumentCaptor<PayrollRun> captor =
                org.mockito.ArgumentCaptor.forClass(PayrollRun.class);
        verify(runs).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PayrollRunStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("leave lookup exploded");
    }

    // --- Sprint 24K §8.2/§8.3 — recurring/one-time TDS split and adjustment audit trail ---

    @Test
    void splitsRecurringAndOneTimeGrossIntoTdsInputAndLogsAdjustmentsForAudit() {
        PayrollRunService realCalcService =
                new PayrollRunService(
                        runs,
                        payslips,
                        periods,
                        compensations,
                        new PayrollCalculator(),
                        lop,
                        arrears,
                        leaves,
                        new PayrollPolicy(),
                        new PayrollMapper(),
                        events,
                        guard,
                        statutoryConfigResolver,
                        pfCalculationService,
                        esiCalculationService,
                        professionalTaxCalculationService,
                        lwfCalculationService,
                        incomeTaxCalculationService,
                        payrollProfiles,
                        esiEnrollments,
                        taxDeclarations,
                        validator,
                        tdsAdjustmentLogs);

        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();

        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setPeriodStart(java.time.LocalDate.of(2026, 4, 1));
        period.setPeriodEnd(java.time.LocalDate.of(2026, 4, 30));
        period.setPayDate(java.time.LocalDate.of(2026, 5, 1));
        when(periods.require(tenantId, periodId)).thenReturn(period);

        com.ewos.employee.domain.Employee employee = new com.ewos.employee.domain.Employee();
        employee.setId(employeeId);
        employee.setEmployeeNumber("E100");
        employee.setDisplayName("Test Employee");
        employee.setHireDate(java.time.LocalDate.of(2020, 1, 1));

        com.ewos.payroll.domain.PayComponent bonusComponent =
                new com.ewos.payroll.domain.PayComponent();
        bonusComponent.setCode("BONUS");
        bonusComponent.setName("Performance Bonus");
        bonusComponent.setKind(com.ewos.payroll.domain.PayComponentKind.EARNING);
        bonusComponent.setCalculationType(
                com.ewos.payroll.domain.PayComponentCalculationType.FIXED);
        bonusComponent.setRecurring(false);
        bonusComponent.setActive(true);
        bonusComponent.setSortOrder(50);

        com.ewos.payroll.domain.EmployeeCompensationLine bonusLine =
                new com.ewos.payroll.domain.EmployeeCompensationLine();
        bonusLine.setPayComponent(bonusComponent);
        bonusLine.setAmount(new java.math.BigDecimal("50000"));

        com.ewos.payroll.domain.EmployeeCompensation comp =
                new com.ewos.payroll.domain.EmployeeCompensation();
        comp.setTenantId(tenantId);
        comp.setCompanyId(companyId);
        comp.setEmployee(employee);
        comp.setBasicSalary(new java.math.BigDecimal("40000"));
        comp.setCurrency("INR");
        comp.addLine(bonusLine);

        when(compensations.activeForCompany(tenantId, companyId)).thenReturn(List.of(comp));
        when(lop.weekdaysBetween(any(), any())).thenReturn(java.math.BigDecimal.valueOf(22));
        when(lop.computeLopDays(any(), any(), any())).thenReturn(java.math.BigDecimal.ZERO);
        when(leaves.findApprovedOverlappingForEmployees(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(arrears.findPendingForEmployees(any(), any())).thenReturn(List.of());

        com.ewos.payroll.domain.EmployeePayrollProfile profile =
                new com.ewos.payroll.domain.EmployeePayrollProfile();
        profile.setEmployee(employee);
        profile.setCompanyId(companyId);
        profile.setTaxRegime("NEW");
        when(payrollProfiles.findAllActiveForEmployees(tenantId, List.of(employeeId)))
                .thenReturn(List.of(profile));
        when(esiEnrollments.findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
                        any(), any(), any()))
                .thenReturn(List.of());
        when(taxDeclarations.findAllByTenantIdAndEmployeeIdInAndFiscalYearAndActiveTrue(
                        any(), any(), any()))
                .thenReturn(List.of());
        when(taxDeclarations.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StatutoryConfigResolver.ConfigSnapshot snapshot =
                new StatutoryConfigResolver.ConfigSnapshot(
                        null, null, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                        Map.of());
        when(statutoryConfigResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(snapshot);

        when(pfCalculationService.calculate(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        org.mockito.ArgumentMatchers.anyBoolean(),
                        any()))
                .thenReturn(
                        new PfCalculationService.PfResult(
                                java.math.BigDecimal.ZERO,
                                java.math.BigDecimal.ZERO,
                                java.math.BigDecimal.ZERO,
                                java.math.BigDecimal.ZERO));
        when(esiCalculationService.calculate(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new EsiCalculationService.EsiResult(
                                java.math.BigDecimal.ZERO,
                                java.math.BigDecimal.ZERO,
                                false,
                                null,
                                false));
        when(professionalTaxCalculationService.calculate(any(), any(), any(), any()))
                .thenReturn(java.math.BigDecimal.ZERO);
        when(lwfCalculationService.calculate(any(), any()))
                .thenReturn(
                        new LwfCalculationService.LwfResult(
                                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));

        IncomeTaxCalculationService.TdsResult tdsResult =
                new IncomeTaxCalculationService.TdsResult(
                        new java.math.BigDecimal("5000.00"),
                        new java.math.BigDecimal("1000.00"),
                        new java.math.BigDecimal("4000.00"),
                        new java.math.BigDecimal("200.00"),
                        new java.math.BigDecimal("12000.00"),
                        new java.math.BigDecimal("8000.00"),
                        new java.math.BigDecimal("480000.00"),
                        new java.math.BigDecimal("400000.00"),
                        java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO);
        org.mockito.ArgumentCaptor<IncomeTaxCalculationService.TdsInput> tdsInputCaptor =
                org.mockito.ArgumentCaptor.forClass(IncomeTaxCalculationService.TdsInput.class);
        when(incomeTaxCalculationService.calculate(
                        any(), any(), any(), any(), tdsInputCaptor.capture()))
                .thenReturn(tdsResult);

        when(payslips.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PayrollRunResponse response =
                realCalcService.start(new StartPayrollRunRequest(tenantId, companyId, periodId));

        assertThat(response.status()).isEqualTo(PayrollRunStatus.COMPLETED);

        IncomeTaxCalculationService.TdsInput capturedInput = tdsInputCaptor.getValue();
        // Basic (40000, implicit recurring line) vs. the non-recurring bonus (50000) must be
        // split apart rather than both annualised together.
        assertThat(capturedInput.monthlyRecurringTaxableSalary()).isEqualByComparingTo("40000");
        assertThat(capturedInput.oneTimePaymentThisPeriod()).isEqualByComparingTo("50000");
        assertThat(capturedInput.payableEarningsThisPeriod()).isEqualByComparingTo("90000");

        // Both a shortfall (§8.2) and an incremental variable-payment recovery (§8.3) occurred
        // this period, so two audit rows must be written.
        org.mockito.ArgumentCaptor<TdsAdjustmentLog> logCaptor =
                org.mockito.ArgumentCaptor.forClass(TdsAdjustmentLog.class);
        verify(tdsAdjustmentLogs, org.mockito.Mockito.times(2)).save(logCaptor.capture());
        List<TdsAdjustmentType> types =
                logCaptor.getAllValues().stream().map(TdsAdjustmentLog::getAdjustmentType).toList();
        assertThat(types)
                .containsExactlyInAnyOrder(
                        com.ewos.payroll.domain.TdsAdjustmentType.SHORTFALL_CAP,
                        com.ewos.payroll.domain.TdsAdjustmentType.VARIABLE_PAYMENT_INCREMENTAL);

        // The recurring-only recovery goes to ytdTdsDeducted; the incremental bonus recovery
        // goes to the separate ytdVariablePaymentTdsRecovered accumulator instead (never mixed).
        org.mockito.ArgumentCaptor<EmployeeTaxDeclaration> declarationCaptor =
                org.mockito.ArgumentCaptor.forClass(EmployeeTaxDeclaration.class);
        verify(taxDeclarations, org.mockito.Mockito.atLeastOnce())
                .save(declarationCaptor.capture());
        EmployeeTaxDeclaration savedDeclaration =
                declarationCaptor.getAllValues().get(declarationCaptor.getAllValues().size() - 1);
        assertThat(savedDeclaration.getYtdTdsDeducted()).isEqualByComparingTo("1000.00");
        assertThat(savedDeclaration.getYtdVariablePaymentTdsRecovered())
                .isEqualByComparingTo("4000.00");
    }
}
