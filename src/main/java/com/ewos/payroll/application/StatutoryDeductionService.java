package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.StatutoryDeductionResponse;
import com.ewos.payroll.application.EsiCalculationService.EsiResult;
import com.ewos.payroll.application.LwfCalculationService.LwfResult;
import com.ewos.payroll.application.PfCalculationService.PfResult;
import com.ewos.payroll.application.StatutoryConfigResolver.ConfigSnapshot;
import com.ewos.payroll.domain.EmployeeEsiEnrollment;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.FiscalYear;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.StatutoryClassifier;
import com.ewos.payroll.domain.StatutoryDeduction;
import com.ewos.payroll.infrastructure.persistence.EmployeeEsiEnrollmentRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.payroll.infrastructure.persistence.StatutoryDeductionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans finalized payslips for statutory deduction lines and materialises one {@link
 * StatutoryDeduction} row per (payslip, statutory code), including the employer-side share for PF
 * (split into the EPS carve-out and the residual EPF), ESI, and LWF — recomputed here from each
 * payslip's already-persisted gross wage via the same calculation services {@link
 * PayrollRunService} used for the employee-side deduction, so the two sides of one contribution are
 * always derived from one formula. Professional Tax and TDS have no employer-side contribution by
 * law, so those rows keep {@code employerContribution = 0}. A payslip whose employee has no {@link
 * EmployeePayrollProfile} yields no employer contribution for that payslip's PF/ESI/LWF rows —
 * consistent with {@link PayrollRunService}, which likewise skips the employee-side deduction when
 * the profile is absent.
 */
@Service
@Transactional
public class StatutoryDeductionService {

    private static final Set<String> PF_CODES = Set.of("PF");
    private static final Set<String> ESI_CODES = Set.of("ESI");
    private static final Set<String> LWF_CODES = Set.of("LWF");

    private final StatutoryDeductionRepository repository;
    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final StatutoryClassifier classifier;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;
    private final StatutoryConfigResolver statutoryConfigResolver;
    private final PfCalculationService pfCalculationService;
    private final EsiCalculationService esiCalculationService;
    private final LwfCalculationService lwfCalculationService;
    private final EmployeePayrollProfileRepository payrollProfiles;
    private final EmployeeEsiEnrollmentRepository esiEnrollments;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public StatutoryDeductionService(
            StatutoryDeductionRepository repository,
            PayrollRunRepository runs,
            PayslipRepository payslips,
            StatutoryClassifier classifier,
            PayrollMapper mapper,
            ClientAccessGuard guard,
            StatutoryConfigResolver statutoryConfigResolver,
            PfCalculationService pfCalculationService,
            EsiCalculationService esiCalculationService,
            LwfCalculationService lwfCalculationService,
            EmployeePayrollProfileRepository payrollProfiles,
            EmployeeEsiEnrollmentRepository esiEnrollments) {
        this.repository = repository;
        this.runs = runs;
        this.payslips = payslips;
        this.classifier = classifier;
        this.mapper = mapper;
        this.guard = guard;
        this.statutoryConfigResolver = statutoryConfigResolver;
        this.pfCalculationService = pfCalculationService;
        this.esiCalculationService = esiCalculationService;
        this.lwfCalculationService = lwfCalculationService;
        this.payrollProfiles = payrollProfiles;
        this.esiEnrollments = esiEnrollments;
    }

    /**
     * Extract statutory deductions from every payslip on the given run. Idempotent per (payslip,
     * code) — the unique index on {@code payslip_id, LOWER(code)} guarantees the DB rejects a
     * second insert; this method skips codes that already exist.
     */
    public int extractForRun(UUID tenantId, UUID runId) {
        PayrollRun run =
                runs.findByIdAndTenantId(runId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Payroll run not found"));
        guard.requireAccessForCompany(run.getCompanyId());

        List<Payslip> slips = payslips.findAllForRun(tenantId, runId);
        PayrollPeriod period = run.getPayrollPeriod();

        Map<UUID, EmployeePayrollProfile> profileByEmployee = new HashMap<>();
        ConfigSnapshot statutoryConfig = null;
        Map<UUID, EmployeeEsiEnrollment> esiEnrollmentByEmployee = new HashMap<>();
        if (period != null && period.getPeriodStart() != null && !slips.isEmpty()) {
            List<UUID> employeeIds =
                    slips.stream()
                            .map(Payslip::getEmployee)
                            .filter(java.util.Objects::nonNull)
                            .map(Employee::getId)
                            .distinct()
                            .toList();
            if (!employeeIds.isEmpty()) {
                profileByEmployee.putAll(
                        payrollProfiles.findAllActiveForEmployees(tenantId, employeeIds).stream()
                                .collect(
                                        Collectors.toMap(
                                                p -> p.getEmployee().getId(),
                                                p -> p,
                                                (a, b) -> a)));
                LocalDate esiPeriodStart =
                        esiCalculationService.contributionPeriodStart(period.getPeriodStart());
                esiEnrollmentByEmployee.putAll(
                        esiEnrollments
                                .findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
                                        tenantId, employeeIds, esiPeriodStart)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                e -> e.getEmployee().getId(),
                                                e -> e,
                                                (a, b) -> a)));
            }
            Set<String> stateCodes =
                    profileByEmployee.values().stream()
                            .map(EmployeePayrollProfile::getStateCode)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());
            statutoryConfig =
                    statutoryConfigResolver.resolve(
                            tenantId,
                            run.getCompanyId(),
                            period.getPeriodStart(),
                            FiscalYear.labelFor(period.getPeriodStart()),
                            stateCodes);
        }

        int inserted = 0;
        for (Payslip slip : slips) {
            Map<String, StatutoryDeduction> existingByCode = new HashMap<>();
            for (StatutoryDeduction d : repository.findAllForPayslip(tenantId, slip.getId())) {
                existingByCode.put(d.getCode().toLowerCase(java.util.Locale.ROOT), d);
            }
            int periodMonth =
                    slip.getPeriodStart() == null
                            ? 0
                            : slip.getPeriodStart().getYear() * 100
                                    + slip.getPeriodStart().getMonthValue();
            BigDecimal basic =
                    slip.getBasicEffective() != null ? slip.getBasicEffective() : BigDecimal.ZERO;
            EmployeePayrollProfile profile =
                    slip.getEmployee() == null
                            ? null
                            : profileByEmployee.get(slip.getEmployee().getId());

            for (PayslipLine line : slip.getLines()) {
                if (line.getKind() != PayComponentKind.DEDUCTION) {
                    continue;
                }
                var cls = classifier.classify(line.getComponentCodeSnapshot());
                if (cls.isEmpty()) {
                    continue;
                }
                String code = cls.get().code();
                String codeKey = code.toLowerCase(java.util.Locale.ROOT);
                if (existingByCode.containsKey(codeKey)) {
                    continue;
                }

                BigDecimal employerContribution = BigDecimal.ZERO;
                BigDecimal epsContribution = BigDecimal.ZERO;
                if (profile != null && statutoryConfig != null) {
                    if (PF_CODES.contains(code)) {
                        PfResult pf =
                                pfCalculationService.calculate(
                                        statutoryConfig.pfConfiguration(),
                                        slip.getGrossAmount(),
                                        profile.isInternationalWorker(),
                                        profile.isVpfEnabled(),
                                        profile.getVpfPercentage());
                        employerContribution =
                                pf.employerPfContribution().add(pf.epsContribution());
                        epsContribution = pf.epsContribution();
                    } else if (ESI_CODES.contains(code)) {
                        EsiResult esi =
                                esiCalculationService.calculate(
                                        statutoryConfig.esiConfiguration(),
                                        Optional.ofNullable(
                                                esiEnrollmentByEmployee.get(
                                                        slip.getEmployee().getId())),
                                        slip.getPeriodStart(),
                                        slip.getEmployee().getHireDate(),
                                        slip.getGrossAmount(),
                                        slip.getEmployee(),
                                        tenantId,
                                        run.getCompanyId());
                        employerContribution = esi.employerContribution();
                    } else if (LWF_CODES.contains(code)) {
                        LwfResult lwf =
                                lwfCalculationService.calculate(
                                        statutoryConfig.lwfConfigurationFor(profile.getStateCode()),
                                        slip.getPeriodStart());
                        employerContribution = lwf.employerContribution();
                    }
                }

                StatutoryDeduction d = new StatutoryDeduction();
                d.setTenantId(tenantId);
                d.setCompanyId(slip.getCompanyId());
                d.setPayrollRun(run);
                d.setPayslip(slip);
                d.setEmployee(slip.getEmployee());
                d.setJurisdiction(cls.get().jurisdiction());
                d.setCode(code);
                d.setPeriodMonth(periodMonth);
                d.setTaxableBase(basic);
                d.setEmployeeContribution(line.getAmount());
                d.setEmployerContribution(employerContribution);
                d.setEpsContribution(epsContribution);
                d.setTotalAmount(line.getAmount().add(employerContribution));
                d.setCurrency(slip.getCurrency());
                repository.save(d);
                existingByCode.put(codeKey, d);
                inserted++;
            }
        }
        return inserted;
    }

    @Transactional(readOnly = true)
    public StatutoryDeductionResponse getById(UUID tenantId, UUID id) {
        StatutoryDeduction d =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "Statutory deduction not found"));
        guard.requireAccessForCompany(d.getCompanyId());
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forPayslip(UUID tenantId, UUID payslipId) {
        List<StatutoryDeduction> found = repository.findAllForPayslip(tenantId, payslipId);
        guard.requireAccessForCompanies(
                found.stream().map(StatutoryDeduction::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forRun(UUID tenantId, UUID runId) {
        List<StatutoryDeduction> found = repository.findAllForRun(tenantId, runId);
        guard.requireAccessForCompanies(
                found.stream().map(StatutoryDeduction::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StatutoryDeductionResponse> forEmployeeMonth(
            UUID tenantId, UUID employeeId, int periodMonth) {
        List<StatutoryDeduction> found =
                repository.findForEmployeeMonth(tenantId, employeeId, periodMonth);
        guard.requireAccessForCompanies(
                found.stream().map(StatutoryDeduction::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }
}
