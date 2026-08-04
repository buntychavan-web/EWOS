package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollSimulationLineResponse;
import com.ewos.payroll.api.dto.PayrollSimulationReportResponse;
import com.ewos.payroll.application.EsiCalculationService.EsiResult;
import com.ewos.payroll.application.LwfCalculationService.LwfResult;
import com.ewos.payroll.application.PfCalculationService.PfResult;
import com.ewos.payroll.application.StatutoryConfigResolver.ConfigSnapshot;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.infrastructure.persistence.EmployeeEsiEnrollmentRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollSimulationServiceTest {

    @Mock PayrollPeriodService periods;
    @Mock EmployeeCompensationService compensations;
    @Mock LopCalculator lop;
    @Mock PayrollArrearRepository arrears;
    @Mock LeaveRequestRepository leaves;
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
    @Mock PayslipRepository payslips;
    @Mock PayrollValidator validator;
    private final PayrollMapper mapper = new PayrollMapper();

    private PayrollSimulationService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID periodId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PayrollSimulationService(
                        periods,
                        compensations,
                        new PayrollCalculator(),
                        lop,
                        arrears,
                        leaves,
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
                        payslips,
                        validator,
                        mapper);

        PayrollPeriod period = new PayrollPeriod();
        period.setId(periodId);
        period.setTenantId(tenantId);
        period.setCompanyId(companyId);
        period.setStatus(PayrollPeriodStatus.LOCKED);
        period.setPeriodStart(LocalDate.of(2026, 8, 1));
        period.setPeriodEnd(LocalDate.of(2026, 8, 31));
        period.setPayDate(LocalDate.of(2026, 9, 1));
        org.mockito.Mockito.lenient().when(periods.require(tenantId, periodId)).thenReturn(period);

        EmployeeCompensation comp = new EmployeeCompensation();
        comp.setTenantId(tenantId);
        comp.setCompanyId(companyId);
        comp.setEmployee(employee());
        comp.setBasicSalary(new BigDecimal("50000"));
        comp.setCurrency("INR");
        org.mockito.Mockito.lenient()
                .when(compensations.activeForCompany(tenantId, companyId))
                .thenReturn(List.of(comp));

        org.mockito.Mockito.lenient()
                .when(lop.weekdaysBetween(any(), any()))
                .thenReturn(BigDecimal.valueOf(22));
        org.mockito.Mockito.lenient()
                .when(lop.computeLopDays(any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        org.mockito.Mockito.lenient()
                .when(leaves.findApprovedOverlappingForEmployees(any(), any(), any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(arrears.findPendingForEmployees(any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(validator.validate(any(), any()))
                .thenReturn(new PayrollValidationReport(List.of(), List.of()));
        org.mockito.Mockito.lenient()
                .when(
                        esiEnrollments.findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
                                any(), any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(
                        taxDeclarations.findAllByTenantIdAndEmployeeIdInAndFiscalYearAndActiveTrue(
                                any(), any(), any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient()
                .when(statutoryConfigResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(
                        new ConfigSnapshot(
                                null, null, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                                Map.of()));
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setEmployeeNumber("E200");
        e.setDisplayName("Sim Employee");
        e.setHireDate(LocalDate.of(2020, 1, 1));
        return e;
    }

    @Test
    void simulateSkipsStatutoryDeductionsAndFlagsTheMissingProfileWhenNoneExists() {
        org.mockito.Mockito.lenient()
                .when(payrollProfiles.findAllActiveForEmployees(tenantId, List.of(employeeId)))
                .thenReturn(List.of());
        when(payslips.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());

        PayrollSimulationReportResponse report = service.simulate(tenantId, companyId, periodId);

        assertThat(report.employeesSimulated()).isEqualTo(1);
        PayrollSimulationLineResponse line = report.lines().get(0);
        assertThat(line.simulatedGross()).isEqualByComparingTo("50000");
        assertThat(line.simulatedTds()).isEqualByComparingTo("0");
        assertThat(line.previousGross()).isNull();
        assertThat(line.abnormalSalaryChange()).isFalse();
        assertThat(line.notes())
                .anyMatch(n -> n.contains("No active payroll profile"))
                .anyMatch(n -> n.contains("No prior payslip"));

        verify(esiEnrollments, never()).save(any());
        verify(taxDeclarations, never()).save(any());
        verify(payslips, never()).save(any());
    }

    @Test
    void simulateFlagsAnAbnormalGrossChangeAgainstThePreviousPayslip() {
        EmployeePayrollProfile profile = new EmployeePayrollProfile();
        profile.setEmployee(employee());
        profile.setCompanyId(companyId);
        profile.setTaxRegime("NEW");
        when(payrollProfiles.findAllActiveForEmployees(tenantId, List.of(employeeId)))
                .thenReturn(List.of(profile));

        when(pfCalculationService.calculate(any(), any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(
                        new PfResult(
                                new BigDecimal("1000"),
                                new BigDecimal("1200"),
                                new BigDecimal("500"),
                                new BigDecimal("50000")));
        when(esiCalculationService.calculate(
                        any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        new EsiResult(
                                new BigDecimal("200"), new BigDecimal("300"), true, null, false));
        when(professionalTaxCalculationService.calculate(any(), any(), any(), any()))
                .thenReturn(new BigDecimal("200"));
        when(lwfCalculationService.calculate(any(), any()))
                .thenReturn(new LwfResult(BigDecimal.TEN, BigDecimal.TEN));
        when(incomeTaxCalculationService.calculate(any(), any(), any(), any(), any()))
                .thenReturn(
                        new IncomeTaxCalculationService.TdsResult(
                                new BigDecimal("5000.00"),
                                new BigDecimal("5000.00"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                new BigDecimal("60000.00"),
                                new BigDecimal("60000.00"),
                                new BigDecimal("600000.00"),
                                new BigDecimal("500000.00"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO));

        Payslip previous = new Payslip();
        previous.setTenantId(tenantId);
        previous.setPeriodStart(LocalDate.of(2026, 7, 1));
        previous.setGrossAmount(new BigDecimal("10000"));
        previous.setNetAmount(new BigDecimal("9000"));
        PayslipLine tdsLine = new PayslipLine();
        tdsLine.setComponentCodeSnapshot("STATUTORY_TDS");
        tdsLine.setAmount(new BigDecimal("800"));
        previous.addLine(tdsLine);
        when(payslips.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(previous));

        PayrollSimulationReportResponse report = service.simulate(tenantId, companyId, periodId);

        PayrollSimulationLineResponse line = report.lines().get(0);
        assertThat(line.simulatedGross()).isEqualByComparingTo("50000");
        assertThat(line.simulatedTds()).isEqualByComparingTo("5000.00");
        assertThat(line.previousGross()).isEqualByComparingTo("10000");
        assertThat(line.previousTds()).isEqualByComparingTo("800");
        assertThat(line.grossChangeAmount()).isEqualByComparingTo("40000");
        assertThat(line.grossChangePercent()).isEqualByComparingTo("400.00");
        assertThat(line.abnormalSalaryChange()).isTrue();
        assertThat(report.abnormalSalaryChangeCount()).isEqualTo(1);
        assertThat(report.totalSimulatedEmployerContributions()).isEqualByComparingTo("1500");

        verify(esiEnrollments, never()).save(any());
        verify(taxDeclarations, never()).save(any());
    }
}
