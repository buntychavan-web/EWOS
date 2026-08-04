package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.api.dto.EmployeeTaxDeclarationResponse;
import com.ewos.payroll.api.dto.PayrollDashboardResponse;
import com.ewos.payroll.api.dto.SelfServiceTaxDeclarationRequest;
import com.ewos.payroll.api.dto.TaxProjectionResponse;
import com.ewos.payroll.api.dto.UpdateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.application.IncomeTaxCalculationService.TdsInput;
import com.ewos.payroll.application.IncomeTaxCalculationService.TdsResult;
import com.ewos.payroll.application.StatutoryConfigResolver.ConfigSnapshot;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.FiscalYear;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregation-only self-service reads for Sprint 24J: a payroll "dashboard" combining data that
 * already exists elsewhere (current compensation, latest payslip, current tax declaration) into one
 * view, plus an on-demand income-tax projection using the exact same calculation the real payroll
 * run uses. No new persisted data model — every field is sourced from an existing entity/service.
 */
@Service
@Transactional(readOnly = true)
public class PayrollSelfServiceService {

    private final EmployeeCompensationService compensations;
    private final PayslipRepository payslips;
    private final EmployeeTaxDeclarationRepository taxDeclarations;
    private final EmployeePayrollProfileRepository payrollProfiles;
    private final StatutoryConfigResolver statutoryConfigResolver;
    private final IncomeTaxCalculationService incomeTaxCalculationService;
    private final EmployeeTaxDeclarationService employeeTaxDeclarationService;
    private final EmployeeRepository employees;
    private final PayrollMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PayrollSelfServiceService(
            EmployeeCompensationService compensations,
            PayslipRepository payslips,
            EmployeeTaxDeclarationRepository taxDeclarations,
            EmployeePayrollProfileRepository payrollProfiles,
            StatutoryConfigResolver statutoryConfigResolver,
            IncomeTaxCalculationService incomeTaxCalculationService,
            EmployeeTaxDeclarationService employeeTaxDeclarationService,
            EmployeeRepository employees,
            PayrollMapper mapper) {
        this.compensations = compensations;
        this.payslips = payslips;
        this.taxDeclarations = taxDeclarations;
        this.payrollProfiles = payrollProfiles;
        this.statutoryConfigResolver = statutoryConfigResolver;
        this.incomeTaxCalculationService = incomeTaxCalculationService;
        this.employeeTaxDeclarationService = employeeTaxDeclarationService;
        this.employees = employees;
        this.mapper = mapper;
    }

    public PayrollDashboardResponse dashboard(UUID tenantId, UUID employeeId) {
        String fiscalYear = FiscalYear.labelFor(LocalDate.now());

        Optional<EmployeeCompensation> compensation =
                compensations.activeForEmployeeOptional(tenantId, employeeId);
        List<Payslip> ownPayslips = payslips.findAllForEmployee(tenantId, employeeId);
        Payslip latest = ownPayslips.isEmpty() ? null : ownPayslips.get(0);
        EmployeeTaxDeclaration declaration =
                taxDeclarations
                        .findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                                tenantId, employeeId, fiscalYear)
                        .orElse(null);

        return new PayrollDashboardResponse(
                fiscalYear,
                compensation.map(mapper::toResponse).orElse(null),
                latest != null ? mapper.toResponse(latest) : null,
                declaration != null ? mapper.toResponse(declaration) : null);
    }

    public TaxProjectionResponse taxProjection(UUID tenantId, UUID employeeId) {
        String fiscalYear = FiscalYear.labelFor(LocalDate.now());
        EmployeePayrollProfile profile =
                payrollProfiles.findActiveForEmployee(tenantId, employeeId).orElse(null);
        TaxRegime regime = regimeFor(profile);

        List<Payslip> ownPayslips = payslips.findAllForEmployee(tenantId, employeeId);
        Payslip latest = ownPayslips.isEmpty() ? null : ownPayslips.get(0);
        EmployeeTaxDeclaration declaration =
                taxDeclarations
                        .findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                                tenantId, employeeId, fiscalYear)
                        .orElse(null);

        BigDecimal monthlyTaxableSalary =
                latest != null ? latest.getGrossAmount() : BigDecimal.ZERO;
        BigDecimal monthlyBasic = latest != null ? latest.getBasicEffective() : BigDecimal.ZERO;
        BigDecimal monthlyHra = latest != null ? findLineAmount(latest, "HRA") : BigDecimal.ZERO;
        int monthsRemaining = FiscalYear.monthsRemaining(LocalDate.now());

        ConfigSnapshot snapshot =
                profile == null
                        ? null
                        : statutoryConfigResolver.resolve(
                                tenantId,
                                profile.getCompanyId(),
                                LocalDate.now(),
                                fiscalYear,
                                Set.of());

        TdsResult result =
                incomeTaxCalculationService.calculate(
                        regime,
                        snapshot != null ? snapshot.incomeTaxSlabsFor(regime) : List.of(),
                        snapshot != null ? snapshot.incomeTaxPolicyFor(regime) : null,
                        snapshot != null ? snapshot.surchargeSlabsFor(regime) : List.of(),
                        new TdsInput(
                                monthlyTaxableSalary,
                                BigDecimal.ZERO,
                                monthlyBasic,
                                monthlyHra,
                                monthsRemaining,
                                monthlyTaxableSalary,
                                declaration));

        return new TaxProjectionResponse(
                fiscalYear,
                regime,
                result.projectedAnnualSalary(),
                result.taxableIncome(),
                result.hraExemption(),
                result.annualTaxLiability(),
                result.monthlyTdsRecovery(),
                latest != null);
    }

    /**
     * Self-service investment declaration: creates the caller's own declaration for the fiscal year
     * if none exists yet, otherwise updates the existing one. {@code tenantId}/{@code
     * companyId}/{@code employeeId} always come from the authenticated caller, never the request
     * body, so an employee can only ever declare for themselves.
     */
    @Transactional
    public EmployeeTaxDeclarationResponse upsertOwnDeclaration(
            UUID tenantId, UUID employeeId, SelfServiceTaxDeclarationRequest request) {
        Optional<EmployeeTaxDeclaration> existing =
                taxDeclarations.findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        tenantId, employeeId, request.fiscalYear());
        if (existing.isPresent()) {
            return employeeTaxDeclarationService.update(
                    tenantId,
                    existing.get().getId(),
                    new UpdateEmployeeTaxDeclarationRequest(
                            request.regime(),
                            request.previousEmployerIncome(),
                            request.otherIncome(),
                            request.housePropertyLoss(),
                            request.chapterViaDeclaredAmount(),
                            request.rentPaidAnnual(),
                            request.metroCity(),
                            request.ltaExemptionDeclared()));
        }
        Employee employee =
                employees
                        .findByIdAndTenantId(employeeId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));
        return employeeTaxDeclarationService.create(
                new CreateEmployeeTaxDeclarationRequest(
                        tenantId,
                        employee.getCompanyId(),
                        employeeId,
                        request.fiscalYear(),
                        request.regime(),
                        request.previousEmployerIncome(),
                        request.otherIncome(),
                        request.housePropertyLoss(),
                        request.chapterViaDeclaredAmount(),
                        request.rentPaidAnnual(),
                        request.metroCity(),
                        request.ltaExemptionDeclared()));
    }

    public EmployeeTaxDeclarationResponse ownDeclaration(
            UUID tenantId, UUID employeeId, String fiscalYear) {
        return employeeTaxDeclarationService.forEmployeeAndYear(tenantId, employeeId, fiscalYear);
    }

    private static BigDecimal findLineAmount(Payslip payslip, String componentCode) {
        return payslip.getLines().stream()
                .filter(l -> componentCode.equalsIgnoreCase(l.getComponentCodeSnapshot()))
                .map(PayslipLine::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static TaxRegime regimeFor(EmployeePayrollProfile profile) {
        if (profile == null || profile.getTaxRegime() == null || profile.getTaxRegime().isBlank()) {
            return TaxRegime.NEW;
        }
        try {
            return TaxRegime.valueOf(profile.getTaxRegime().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return TaxRegime.NEW;
        }
    }
}
