package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollSimulationLineResponse;
import com.ewos.payroll.api.dto.PayrollSimulationReportResponse;
import com.ewos.payroll.application.EsiCalculationService.EsiResult;
import com.ewos.payroll.application.IncomeTaxCalculationService.TdsInput;
import com.ewos.payroll.application.IncomeTaxCalculationService.TdsResult;
import com.ewos.payroll.application.LwfCalculationService.LwfResult;
import com.ewos.payroll.application.PfCalculationService.PfResult;
import com.ewos.payroll.application.StatutoryConfigResolver.ConfigSnapshot;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.EmployeeEsiEnrollment;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.FiscalYear;
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollCalculator.ComputedPayslip;
import com.ewos.payroll.domain.PayrollCalculator.StatutoryAmounts;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.infrastructure.persistence.EmployeeEsiEnrollmentRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payroll Simulation / Dry Run (Sprint 24K, item 1). Runs the exact same calculation pipeline a
 * real payroll run uses — {@link PayrollCalculator}, the PF/ESI/PT/LWF/TDS calculation services,
 * and {@link PayrollValidator} — against the current live data, then discards every result rather
 * than persisting it. No {@code PayrollRun} or {@code Payslip} row is created, no ESI enrollment is
 * created, no {@code EmployeeTaxDeclaration} YTD accumulator is advanced, and no {@code
 * TdsAdjustmentLog} row is written; production payroll data is completely untouched by a
 * simulation, which is the entire point of a dry run. This intentionally reuses every actual
 * calculation building block from {@link PayrollRunService} rather than re-implementing any of them
 * — only the read + compare + discard orchestration here is new.
 *
 * <p>Each employee's simulated result is compared against their most recent payslip from a period
 * before the one being simulated (if any) to flag validation issues, review tax/statutory/employer
 * contribution changes, and detect abnormal salary changes ({@link #ABNORMAL_CHANGE_THRESHOLD_PCT}
 * — an anomaly-detection heuristic, not a statutory figure, so it is a plain constant rather than
 * something requiring statutory verification).
 */
@Service
@Transactional(readOnly = true)
public class PayrollSimulationService {

    /**
     * A simulated gross differing from the employee's previous payslip by more than this percentage
     * is flagged for review before the real run. A data-quality heuristic, not a statutory
     * threshold.
     */
    private static final BigDecimal ABNORMAL_CHANGE_THRESHOLD_PCT = new BigDecimal("25");

    private final PayrollPeriodService periods;
    private final EmployeeCompensationService compensations;
    private final PayrollCalculator calculator;
    private final LopCalculator lop;
    private final PayrollArrearRepository arrears;
    private final LeaveRequestRepository leaves;
    private final ClientAccessGuard guard;
    private final StatutoryConfigResolver statutoryConfigResolver;
    private final PfCalculationService pfCalculationService;
    private final EsiCalculationService esiCalculationService;
    private final ProfessionalTaxCalculationService professionalTaxCalculationService;
    private final LwfCalculationService lwfCalculationService;
    private final IncomeTaxCalculationService incomeTaxCalculationService;
    private final EmployeePayrollProfileRepository payrollProfiles;
    private final EmployeeEsiEnrollmentRepository esiEnrollments;
    private final EmployeeTaxDeclarationRepository taxDeclarations;
    private final PayslipRepository payslips;
    private final PayrollValidator validator;
    private final PayrollMapper mapper;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PayrollSimulationService(
            PayrollPeriodService periods,
            EmployeeCompensationService compensations,
            PayrollCalculator calculator,
            LopCalculator lop,
            PayrollArrearRepository arrears,
            LeaveRequestRepository leaves,
            ClientAccessGuard guard,
            StatutoryConfigResolver statutoryConfigResolver,
            PfCalculationService pfCalculationService,
            EsiCalculationService esiCalculationService,
            ProfessionalTaxCalculationService professionalTaxCalculationService,
            LwfCalculationService lwfCalculationService,
            IncomeTaxCalculationService incomeTaxCalculationService,
            EmployeePayrollProfileRepository payrollProfiles,
            EmployeeEsiEnrollmentRepository esiEnrollments,
            EmployeeTaxDeclarationRepository taxDeclarations,
            PayslipRepository payslips,
            PayrollValidator validator,
            PayrollMapper mapper) {
        this.periods = periods;
        this.compensations = compensations;
        this.calculator = calculator;
        this.lop = lop;
        this.arrears = arrears;
        this.leaves = leaves;
        this.guard = guard;
        this.statutoryConfigResolver = statutoryConfigResolver;
        this.pfCalculationService = pfCalculationService;
        this.esiCalculationService = esiCalculationService;
        this.professionalTaxCalculationService = professionalTaxCalculationService;
        this.lwfCalculationService = lwfCalculationService;
        this.incomeTaxCalculationService = incomeTaxCalculationService;
        this.payrollProfiles = payrollProfiles;
        this.esiEnrollments = esiEnrollments;
        this.taxDeclarations = taxDeclarations;
        this.payslips = payslips;
        this.validator = validator;
        this.mapper = mapper;
    }

    public PayrollSimulationReportResponse simulate(
            UUID tenantId, UUID companyId, UUID payrollPeriodId) {
        guard.requireAccessForCompany(companyId);
        PayrollPeriod period = periods.require(tenantId, payrollPeriodId);

        List<EmployeeCompensation> active = compensations.activeForCompany(tenantId, companyId);
        List<Employee> employeesInScope =
                active.stream()
                        .map(EmployeeCompensation::getEmployee)
                        .filter(java.util.Objects::nonNull)
                        .toList();
        PayrollValidationReport validationReport = validator.validate(tenantId, employeesInScope);

        BigDecimal workingDays =
                lop.weekdaysBetween(period.getPeriodStart(), period.getPeriodEnd());
        List<UUID> employeeIds = employeesInScope.stream().map(Employee::getId).toList();

        Map<UUID, List<LeaveRequest>> leavesByEmployee =
                employeeIds.isEmpty()
                        ? Map.of()
                        : leaves
                                .findApprovedOverlappingForEmployees(
                                        tenantId,
                                        employeeIds,
                                        period.getPeriodStart(),
                                        period.getPeriodEnd())
                                .stream()
                                .collect(Collectors.groupingBy(lr -> lr.getEmployee().getId()));
        Map<UUID, List<PayrollArrear>> arrearsByEmployee =
                employeeIds.isEmpty()
                        ? Map.of()
                        : arrears.findPendingForEmployees(tenantId, employeeIds).stream()
                                .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

        Map<UUID, EmployeePayrollProfile> profileByEmployee = new HashMap<>();
        Map<UUID, EmployeeEsiEnrollment> esiEnrollmentByEmployee = new HashMap<>();
        Map<UUID, EmployeeTaxDeclaration> taxDeclarationByEmployee = new HashMap<>();
        String fiscalYear = null;
        ConfigSnapshot statutoryConfig = null;
        if (!employeeIds.isEmpty()) {
            profileByEmployee.putAll(
                    payrollProfiles.findAllActiveForEmployees(tenantId, employeeIds).stream()
                            .collect(
                                    Collectors.toMap(
                                            p -> p.getEmployee().getId(), p -> p, (a, b) -> a)));
            fiscalYear = FiscalYear.labelFor(period.getPeriodStart());
            LocalDate esiPeriodStart =
                    esiCalculationService.contributionPeriodStart(period.getPeriodStart());
            esiEnrollmentByEmployee.putAll(
                    esiEnrollments
                            .findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
                                    tenantId, employeeIds, esiPeriodStart)
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            e -> e.getEmployee().getId(), e -> e, (a, b) -> a)));
            taxDeclarationByEmployee.putAll(
                    taxDeclarations
                            .findAllByTenantIdAndEmployeeIdInAndFiscalYearAndActiveTrue(
                                    tenantId, employeeIds, fiscalYear)
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            d -> d.getEmployee().getId(), d -> d, (a, b) -> a)));
            Set<String> stateCodes =
                    profileByEmployee.values().stream()
                            .map(EmployeePayrollProfile::getStateCode)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());
            statutoryConfig =
                    statutoryConfigResolver.resolve(
                            tenantId, companyId, period.getPeriodStart(), fiscalYear, stateCodes);
        }

        List<PayrollSimulationLineResponse> lines = new ArrayList<>();
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalTds = BigDecimal.ZERO;
        BigDecimal totalEmployerContributions = BigDecimal.ZERO;
        int abnormalCount = 0;

        for (EmployeeCompensation comp : active) {
            Employee emp = comp.getEmployee();
            if (emp == null) {
                continue;
            }
            List<LeaveRequest> unpaidOnly =
                    leavesByEmployee.getOrDefault(emp.getId(), List.of()).stream()
                            .filter(lr -> lr.getLeaveType() != null && !lr.getLeaveType().isPaid())
                            .toList();
            BigDecimal lopDays =
                    lop.computeLopDays(unpaidOnly, period.getPeriodStart(), period.getPeriodEnd());
            List<PayrollArrear> pendingArrears =
                    arrearsByEmployee.getOrDefault(emp.getId(), List.of());
            EmployeePayrollProfile profile = profileByEmployee.get(emp.getId());

            SimulatedEmployeeResult result =
                    simulateEmployee(
                            tenantId,
                            companyId,
                            period,
                            comp,
                            lopDays,
                            workingDays,
                            pendingArrears,
                            emp,
                            profile,
                            statutoryConfig,
                            esiEnrollmentByEmployee,
                            taxDeclarationByEmployee,
                            fiscalYear);

            Payslip previous =
                    mostRecentPreviousPayslip(tenantId, emp.getId(), period.getPeriodStart());
            BigDecimal previousGross = previous != null ? previous.getGrossAmount() : null;
            BigDecimal previousNet = previous != null ? previous.getNetAmount() : null;
            BigDecimal previousTds = previous != null ? findTdsAmount(previous) : null;

            BigDecimal changeAmount =
                    previousGross != null ? result.gross.subtract(previousGross) : null;
            BigDecimal changePercent =
                    (previousGross != null && previousGross.signum() > 0)
                            ? changeAmount
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(previousGross, 2, RoundingMode.HALF_UP)
                            : null;
            boolean abnormal =
                    changePercent != null
                            && changePercent.abs().compareTo(ABNORMAL_CHANGE_THRESHOLD_PCT) > 0;
            if (abnormal) {
                abnormalCount++;
            }

            List<String> notes = new ArrayList<>();
            if (profile == null) {
                notes.add("No active payroll profile — statutory deductions not simulated");
            }
            if (previous == null) {
                notes.add("No prior payslip found — this would be the employee's first payroll");
            }
            if (abnormal) {
                notes.add(
                        "Gross change of "
                                + changePercent
                                + "% exceeds the "
                                + ABNORMAL_CHANGE_THRESHOLD_PCT
                                + "% review threshold");
            }

            lines.add(
                    new PayrollSimulationLineResponse(
                            emp.getId(),
                            emp.getEmployeeNumber(),
                            nameFor(emp),
                            previousGross,
                            previousNet,
                            previousTds,
                            result.gross,
                            result.deductions,
                            result.net,
                            result.tds,
                            result.employerPf,
                            result.employerEsi,
                            changeAmount,
                            changePercent,
                            abnormal,
                            notes));

            totalGross = totalGross.add(result.gross);
            totalDeductions = totalDeductions.add(result.deductions);
            totalNet = totalNet.add(result.net);
            totalTds = totalTds.add(result.tds);
            totalEmployerContributions =
                    totalEmployerContributions.add(result.employerPf).add(result.employerEsi);
        }

        return new PayrollSimulationReportResponse(
                tenantId,
                companyId,
                payrollPeriodId,
                Instant.now(),
                lines.size(),
                totalGross,
                totalDeductions,
                totalNet,
                totalTds,
                totalEmployerContributions,
                abnormalCount,
                mapper.toResponse(
                        tenantId,
                        companyId,
                        payrollPeriodId,
                        employeesInScope.size(),
                        validationReport),
                lines);
    }

    /** Purely computed result for one employee — never persisted anywhere. */
    private record SimulatedEmployeeResult(
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal net,
            BigDecimal tds,
            BigDecimal employerPf,
            BigDecimal employerEsi) {}

    @SuppressWarnings("PMD.ExcessiveParameterList")
    private SimulatedEmployeeResult simulateEmployee(
            UUID tenantId,
            UUID companyId,
            PayrollPeriod period,
            EmployeeCompensation comp,
            BigDecimal lopDays,
            BigDecimal workingDays,
            List<PayrollArrear> pendingArrears,
            Employee emp,
            EmployeePayrollProfile profile,
            ConfigSnapshot statutoryConfig,
            Map<UUID, EmployeeEsiEnrollment> esiEnrollmentByEmployee,
            Map<UUID, EmployeeTaxDeclaration> taxDeclarationByEmployee,
            String fiscalYear) {
        ComputedPayslip preview = calculator.compute(comp, lopDays, workingDays, pendingArrears);
        if (profile == null) {
            return new SimulatedEmployeeResult(
                    preview.gross(),
                    preview.deductions(),
                    preview.net(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO);
        }
        BigDecimal grossWage = preview.gross();

        PfResult pfResult =
                pfCalculationService.calculate(
                        statutoryConfig.pfConfiguration(),
                        grossWage,
                        profile.isInternationalWorker(),
                        profile.isVpfEnabled(),
                        profile.getVpfPercentage());

        Optional<EmployeeEsiEnrollment> existingEnrollment =
                Optional.ofNullable(esiEnrollmentByEmployee.get(emp.getId()));
        EsiResult esiResult =
                esiCalculationService.calculate(
                        statutoryConfig.esiConfiguration(),
                        existingEnrollment,
                        period.getPeriodStart(),
                        emp.getHireDate(),
                        grossWage,
                        emp,
                        tenantId,
                        companyId);

        EmployeeTaxDeclaration declaration = taxDeclarationByEmployee.get(emp.getId());
        if (declaration == null) {
            declaration = new EmployeeTaxDeclaration();
            declaration.setTenantId(tenantId);
            declaration.setCompanyId(companyId);
            declaration.setEmployee(emp);
            declaration.setFiscalYear(fiscalYear);
            declaration.setRegime(regimeFor(profile));
        }

        BigDecimal ptAmount =
                professionalTaxCalculationService.calculate(
                        statutoryConfig.professionalTaxSlabsFor(profile.getStateCode()),
                        emp.getGenderCode(),
                        grossWage,
                        declaration.getYtdProfessionalTaxPaid());

        LwfResult lwfResult =
                lwfCalculationService.calculate(
                        statutoryConfig.lwfConfigurationFor(profile.getStateCode()),
                        period.getPeriodStart());

        TaxRegime regime = regimeFor(profile);
        BigDecimal monthlyHraReceived = findLineAmount(preview, "HRA");
        int monthsRemaining = FiscalYear.monthsRemaining(period.getPeriodStart());
        BigDecimal oneTimeGross = PayrollCalculator.oneTimeGross(preview);
        BigDecimal recurringGross = grossWage.subtract(oneTimeGross).max(BigDecimal.ZERO);
        TdsInput tdsInput =
                new TdsInput(
                        recurringGross,
                        oneTimeGross,
                        preview.basicApplied(),
                        monthlyHraReceived,
                        monthsRemaining,
                        grossWage,
                        declaration);
        TdsResult tdsResult =
                incomeTaxCalculationService.calculate(
                        regime,
                        statutoryConfig.incomeTaxSlabsFor(regime),
                        statutoryConfig.incomeTaxPolicyFor(regime),
                        statutoryConfig.surchargeSlabsFor(regime),
                        tdsInput);

        StatutoryAmounts statutoryAmounts =
                new StatutoryAmounts(
                        pfResult.employeeContribution(),
                        esiResult.employeeContribution(),
                        ptAmount,
                        lwfResult.employeeContribution(),
                        tdsResult.monthlyTdsRecovery());
        ComputedPayslip finalPreview =
                calculator.compute(comp, lopDays, workingDays, pendingArrears, statutoryAmounts);

        return new SimulatedEmployeeResult(
                finalPreview.gross(),
                finalPreview.deductions(),
                finalPreview.net(),
                tdsResult.monthlyTdsRecovery(),
                pfResult.employerPfContribution(),
                esiResult.employerContribution());
    }

    private Payslip mostRecentPreviousPayslip(
            UUID tenantId, UUID employeeId, LocalDate beforeDate) {
        return payslips.findAllForEmployee(tenantId, employeeId).stream()
                .filter(p -> p.getPeriodStart() != null && p.getPeriodStart().isBefore(beforeDate))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal findTdsAmount(Payslip payslip) {
        return payslip.getLines().stream()
                .filter(l -> "STATUTORY_TDS".equalsIgnoreCase(l.getComponentCodeSnapshot()))
                .map(PayslipLine::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal findLineAmount(ComputedPayslip payslip, String componentCode) {
        return payslip.lines().stream()
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

    private static String nameFor(Employee e) {
        if (e.getDisplayName() != null && !e.getDisplayName().isBlank()) {
            return e.getDisplayName();
        }
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        return full.isBlank() ? e.getEmployeeNumber() : full;
    }
}
