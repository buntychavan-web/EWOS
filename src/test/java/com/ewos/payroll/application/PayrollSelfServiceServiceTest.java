package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.api.dto.SelfServiceTaxDeclarationRequest;
import com.ewos.payroll.application.StatutoryConfigResolver.ConfigSnapshot;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.FiscalYear;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayrollSelfServiceServiceTest {

    @Mock EmployeeCompensationService compensations;
    @Mock PayslipRepository payslips;
    @Mock EmployeeTaxDeclarationRepository taxDeclarations;
    @Mock EmployeePayrollProfileRepository payrollProfiles;
    @Mock StatutoryConfigResolver statutoryConfigResolver;
    @Mock IncomeTaxCalculationService incomeTaxCalculationService;
    @Mock EmployeeTaxDeclarationService employeeTaxDeclarationService;
    @Mock EmployeeRepository employees;

    private PayrollSelfServiceService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PayrollSelfServiceService(
                        compensations,
                        payslips,
                        taxDeclarations,
                        payrollProfiles,
                        statutoryConfigResolver,
                        incomeTaxCalculationService,
                        employeeTaxDeclarationService,
                        employees,
                        new PayrollMapper());
    }

    @Test
    void dashboardIsAllNullWhenTheEmployeeHasNothingYet() {
        when(compensations.activeForEmployeeOptional(tenantId, employeeId))
                .thenReturn(Optional.empty());
        when(payslips.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        eq(tenantId), eq(employeeId), any()))
                .thenReturn(Optional.empty());

        var response = service.dashboard(tenantId, employeeId);

        assertThat(response.currentCompensation()).isNull();
        assertThat(response.latestPayslip()).isNull();
        assertThat(response.currentTaxDeclaration()).isNull();
        assertThat(response.fiscalYear()).isEqualTo(FiscalYear.labelFor(LocalDate.now()));
    }

    @Test
    void dashboardAggregatesCompensationLatestPayslipAndDeclaration() {
        EmployeeCompensation comp = new EmployeeCompensation();
        comp.setBasicSalary(new BigDecimal("50000"));
        when(compensations.activeForEmployeeOptional(tenantId, employeeId))
                .thenReturn(Optional.of(comp));
        Payslip slip = new Payslip();
        slip.setGrossAmount(new BigDecimal("60000"));
        when(payslips.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(slip));
        EmployeeTaxDeclaration decl = new EmployeeTaxDeclaration();
        when(taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        eq(tenantId), eq(employeeId), any()))
                .thenReturn(Optional.of(decl));

        var response = service.dashboard(tenantId, employeeId);

        assertThat(response.currentCompensation().basicSalary()).isEqualByComparingTo("50000");
        assertThat(response.latestPayslip().grossAmount()).isEqualByComparingTo("60000");
        assertThat(response.currentTaxDeclaration()).isNotNull();
    }

    @Test
    void taxProjectionUsesTheLatestPayslipsGrossAndBasicEffective() {
        EmployeePayrollProfile profile = new EmployeePayrollProfile();
        profile.setCompanyId(UUID.randomUUID());
        profile.setTaxRegime("NEW");
        when(payrollProfiles.findActiveForEmployee(tenantId, employeeId))
                .thenReturn(Optional.of(profile));
        Payslip slip = new Payslip();
        slip.setGrossAmount(new BigDecimal("100000"));
        slip.setBasicEffective(new BigDecimal("50000"));
        when(payslips.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(slip));
        when(taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        eq(tenantId), eq(employeeId), any()))
                .thenReturn(Optional.empty());

        IncomeTaxPolicy policy = new IncomeTaxPolicy();
        ConfigSnapshot snapshot =
                new ConfigSnapshot(
                        null,
                        null,
                        null,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(TaxRegime.NEW, List.<IncomeTaxSlab>of()),
                        Map.of(TaxRegime.NEW, policy),
                        Map.of());
        when(statutoryConfigResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(snapshot);
        IncomeTaxCalculationService.TdsResult result =
                new IncomeTaxCalculationService.TdsResult(
                        new BigDecimal("1000"),
                        new BigDecimal("12000"),
                        new BigDecimal("1200000"),
                        new BigDecimal("1100000"),
                        new BigDecimal("0"),
                        new BigDecimal("0"));
        when(incomeTaxCalculationService.calculate(
                        org.mockito.ArgumentMatchers.eq(TaxRegime.NEW), any(), any(), any(), any()))
                .thenReturn(result);

        var response = service.taxProjection(tenantId, employeeId);

        assertThat(response.regime()).isEqualTo(TaxRegime.NEW);
        assertThat(response.annualTaxLiability()).isEqualByComparingTo("12000");
        assertThat(response.basedOnLatestPayslip()).isTrue();
    }

    @Test
    void upsertOwnDeclarationCreatesWhenNoneExistsYet() {
        SelfServiceTaxDeclarationRequest request =
                new SelfServiceTaxDeclarationRequest(
                        "2026-27", TaxRegime.NEW, null, null, null, null, null, null, null);
        when(taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, "2026-27"))
                .thenReturn(Optional.empty());
        Employee employee = new Employee();
        UUID companyId = UUID.randomUUID();
        employee.setCompanyId(companyId);
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        service.upsertOwnDeclaration(tenantId, employeeId, request);

        org.mockito.ArgumentCaptor<CreateEmployeeTaxDeclarationRequest> captor =
                org.mockito.ArgumentCaptor.forClass(CreateEmployeeTaxDeclarationRequest.class);
        org.mockito.Mockito.verify(employeeTaxDeclarationService).create(captor.capture());
        assertThat(captor.getValue().employeeId()).isEqualTo(employeeId);
        assertThat(captor.getValue().companyId()).isEqualTo(companyId);
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void upsertOwnDeclarationUpdatesWhenOneAlreadyExists() {
        SelfServiceTaxDeclarationRequest request =
                new SelfServiceTaxDeclarationRequest(
                        "2026-27", TaxRegime.OLD, null, null, null, null, null, null, null);
        EmployeeTaxDeclaration existing = new EmployeeTaxDeclaration();
        UUID declarationId = UUID.randomUUID();
        existing.setId(declarationId);
        when(taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, "2026-27"))
                .thenReturn(Optional.of(existing));

        service.upsertOwnDeclaration(tenantId, employeeId, request);

        org.mockito.Mockito.verify(employeeTaxDeclarationService)
                .update(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(declarationId),
                        any());
        org.mockito.Mockito.verifyNoInteractions(employees);
    }
}
