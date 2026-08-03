package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.PayrollRunTimelineEventResponse;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
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
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollCalculator.ComputedPayslip;
import com.ewos.payroll.domain.PayrollCalculator.StatutoryAmounts;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.PayrollRunType;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.PayrollValidator;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipStatus;
import com.ewos.payroll.domain.TaxRegime;
import com.ewos.payroll.domain.TdsAdjustmentLog;
import com.ewos.payroll.domain.TdsAdjustmentType;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.EmployeeEsiEnrollmentRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.payroll.infrastructure.persistence.TdsAdjustmentLogRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates payroll runs.
 *
 * <ul>
 *   <li>{@link #start(StartPayrollRunRequest)} — reserves a {@link PayrollRun}, transitions to
 *       {@code PROCESSING}, generates a {@code DRAFT} payslip per active-compensation employee in
 *       the company (consuming LOP from approved unpaid leave and pending arrears), then lands in
 *       {@code COMPLETED} with aggregate totals.
 *   <li>{@link #finalizeRun(UUID, UUID)} — flips every payslip on the run to {@code FINALIZED}.
 *   <li>{@link #freeze(UUID, UUID)} — terminal lock; no supplementary or corrective run may adjust
 *       this run's payslips.
 * </ul>
 */
@Service
@Transactional
public class PayrollRunService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollPeriodService periods;
    private final EmployeeCompensationService compensations;
    private final PayrollCalculator calculator;
    private final LopCalculator lop;
    private final PayrollArrearRepository arrears;
    private final LeaveRequestRepository leaves;
    private final PayrollPolicy policy;
    private final PayrollMapper mapper;
    private final ApplicationEventPublisher events;
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
    private final PayrollValidator validator;
    private final TdsAdjustmentLogRepository tdsAdjustmentLogs;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PayrollRunService(
            PayrollRunRepository runs,
            PayslipRepository payslips,
            PayrollPeriodService periods,
            EmployeeCompensationService compensations,
            PayrollCalculator calculator,
            LopCalculator lop,
            PayrollArrearRepository arrears,
            LeaveRequestRepository leaves,
            PayrollPolicy policy,
            PayrollMapper mapper,
            ApplicationEventPublisher events,
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
            PayrollValidator validator,
            TdsAdjustmentLogRepository tdsAdjustmentLogs) {
        this.runs = runs;
        this.payslips = payslips;
        this.periods = periods;
        this.compensations = compensations;
        this.calculator = calculator;
        this.lop = lop;
        this.arrears = arrears;
        this.leaves = leaves;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
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
        this.validator = validator;
        this.tdsAdjustmentLogs = tdsAdjustmentLogs;
    }

    public PayrollRunResponse start(StartPayrollRunRequest request) {
        guard.requireAccessForCompany(request.companyId());
        PayrollPeriod period = periods.require(request.tenantId(), request.payrollPeriodId());
        if (!period.getCompanyId().equals(request.companyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Period belongs to a different company");
        }
        policy.assertRunnable(period);
        return doStart(period, PayrollRunType.REGULAR, null);
    }

    /**
     * Off-cycle supplementary run: processes only the given employees against the given period. The
     * period does not need to be LOCKED — supplementary runs are corrections and may target any
     * period status other than CLOSED.
     */
    public PayrollRunResponse startSupplementary(
            UUID tenantId, UUID companyId, UUID payrollPeriodId, List<UUID> employeeIds) {
        guard.requireAccessForCompany(companyId);
        if (employeeIds == null || employeeIds.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Supplementary run requires at least one employee");
        }
        PayrollPeriod period = periods.require(tenantId, payrollPeriodId);
        if (!period.getCompanyId().equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Period belongs to a different company");
        }
        if (period.getStatus() == com.ewos.payroll.domain.PayrollPeriodStatus.CLOSED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot run supplementary payroll against a CLOSED period");
        }
        return doStart(period, PayrollRunType.SUPPLEMENTARY, employeeIds);
    }

    /** Internal: creates a FINAL_SETTLEMENT run for a single employee. */
    public PayrollRun startFinalSettlement(
            UUID tenantId, UUID companyId, UUID periodId, UUID employeeId) {
        PayrollPeriod period = periods.require(tenantId, periodId);
        if (!period.getCompanyId().equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Period belongs to a different company");
        }
        PayrollRunResponse resp =
                doStart(period, PayrollRunType.FINAL_SETTLEMENT, List.of(employeeId));
        return runs.findByIdAndTenantId(resp.id(), tenantId).orElseThrow();
    }

    private PayrollRunResponse doStart(
            PayrollPeriod period, PayrollRunType runType, List<UUID> employeeFilter) {
        UUID actor = requireActor();
        PayrollRun run = new PayrollRun();
        run.setTenantId(period.getTenantId());
        run.setCompanyId(period.getCompanyId());
        run.setPayrollPeriod(period);
        run.setStatus(PayrollRunStatus.PENDING);
        run.setRunType(runType);
        PayrollRun saved = runs.save(run);
        publishRun(PayrollEventType.RUN_STARTED, saved);

        List<EmployeeCompensation> active =
                (employeeFilter == null)
                        ? compensations.activeForCompany(saved.getTenantId(), saved.getCompanyId())
                        : compensations.activeForEmployeeIds(saved.getTenantId(), employeeFilter);
        List<Employee> employeesInScope =
                active.stream()
                        .map(EmployeeCompensation::getEmployee)
                        .filter(e -> e != null)
                        .toList();
        recordValidationReport(saved, validator.validate(saved.getTenantId(), employeesInScope));

        saved.setStatus(PayrollRunStatus.PROCESSING);
        saved.setStartedAt(Instant.now());
        saved.setStartedBy(actor);
        processPayslips(saved, period, active);
        return mapper.toResponse(saved);
    }

    private void processPayslips(
            PayrollRun run, PayrollPeriod period, List<EmployeeCompensation> active) {

        BigDecimal workingDays =
                lop.weekdaysBetween(period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int processed = 0;

        try {
            // Bulk-fetch once for the whole run instead of once per employee — at 1,000+
            // employees the per-employee queries this replaced were the dominant cost (see
            // Sprint 18's performance benchmark). Grouping by employee id in memory is cheap;
            // lr.getEmployee().getId() / a.getEmployee().getId() never hit the database even
            // though the association isn't fetch-joined, because Hibernate's lazy proxy already
            // knows its id from the foreign key column that was part of each bulk row.
            List<UUID> employeeIds =
                    active.stream()
                            .map(EmployeeCompensation::getEmployee)
                            .filter(java.util.Objects::nonNull)
                            .map(Employee::getId)
                            .toList();

            Map<UUID, List<LeaveRequest>> leavesByEmployee =
                    employeeIds.isEmpty()
                            ? Map.of()
                            : leaves
                                    .findApprovedOverlappingForEmployees(
                                            run.getTenantId(),
                                            employeeIds,
                                            period.getPeriodStart(),
                                            period.getPeriodEnd())
                                    .stream()
                                    .collect(Collectors.groupingBy(lr -> lr.getEmployee().getId()));

            Map<UUID, List<PayrollArrear>> arrearsByEmployee =
                    employeeIds.isEmpty()
                            ? Map.of()
                            : arrears
                                    .findPendingForEmployees(run.getTenantId(), employeeIds)
                                    .stream()
                                    .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

            Map<UUID, EmployeePayrollProfile> profileByEmployee = new HashMap<>();
            Map<UUID, EmployeeEsiEnrollment> esiEnrollmentByEmployee = new HashMap<>();
            Map<UUID, EmployeeTaxDeclaration> taxDeclarationByEmployee = new HashMap<>();
            String fiscalYear = null;
            ConfigSnapshot statutoryConfig = null;
            if (!employeeIds.isEmpty()) {
                profileByEmployee.putAll(
                        payrollProfiles
                                .findAllActiveForEmployees(run.getTenantId(), employeeIds)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                p -> p.getEmployee().getId(),
                                                p -> p,
                                                (a, b) -> a)));

                fiscalYear = com.ewos.payroll.domain.FiscalYear.labelFor(period.getPeriodStart());
                LocalDate esiPeriodStart =
                        esiCalculationService.contributionPeriodStart(period.getPeriodStart());
                esiEnrollmentByEmployee.putAll(
                        esiEnrollments
                                .findAllByTenantIdAndEmployeeIdInAndContributionPeriodStart(
                                        run.getTenantId(), employeeIds, esiPeriodStart)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                e -> e.getEmployee().getId(),
                                                e -> e,
                                                (a, b) -> a)));

                taxDeclarationByEmployee.putAll(
                        taxDeclarations
                                .findAllByTenantIdAndEmployeeIdInAndFiscalYearAndActiveTrue(
                                        run.getTenantId(), employeeIds, fiscalYear)
                                .stream()
                                .collect(
                                        Collectors.toMap(
                                                d -> d.getEmployee().getId(),
                                                d -> d,
                                                (a, b) -> a)));

                Set<String> stateCodes =
                        profileByEmployee.values().stream()
                                .map(EmployeePayrollProfile::getStateCode)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet());
                statutoryConfig =
                        statutoryConfigResolver.resolve(
                                run.getTenantId(),
                                run.getCompanyId(),
                                period.getPeriodStart(),
                                fiscalYear,
                                stateCodes);
            }

            for (EmployeeCompensation comp : active) {
                Employee emp = comp.getEmployee();
                if (emp == null) {
                    continue;
                }

                List<LeaveRequest> approvedInPeriod =
                        leavesByEmployee.getOrDefault(emp.getId(), List.of());
                List<LeaveRequest> unpaidOnly =
                        approvedInPeriod.stream()
                                .filter(
                                        lr ->
                                                lr.getLeaveType() != null
                                                        && !lr.getLeaveType().isPaid())
                                .toList();
                BigDecimal lopDays =
                        lop.computeLopDays(
                                unpaidOnly, period.getPeriodStart(), period.getPeriodEnd());

                List<PayrollArrear> pendingArrears =
                        arrearsByEmployee.getOrDefault(emp.getId(), List.of());

                EmployeePayrollProfile profile = profileByEmployee.get(emp.getId());
                StatutoryAmounts statutoryAmounts =
                        resolveStatutoryAmounts(
                                run,
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

                ComputedPayslip computed =
                        calculator.compute(
                                comp, lopDays, workingDays, pendingArrears, statutoryAmounts);

                Payslip payslip = new Payslip();
                payslip.setTenantId(run.getTenantId());
                payslip.setCompanyId(run.getCompanyId());
                payslip.setPayrollRun(run);
                payslip.setPayrollPeriod(period);
                payslip.setEmployee(emp);
                payslip.setEmployeeNumberSnapshot(emp.getEmployeeNumber());
                payslip.setEmployeeNameSnapshot(nameFor(emp));
                payslip.setPeriodStart(period.getPeriodStart());
                payslip.setPeriodEnd(period.getPeriodEnd());
                payslip.setPayDate(period.getPayDate());
                payslip.setCurrency(comp.getCurrency());
                payslip.setGrossAmount(computed.gross());
                payslip.setDeductionsAmount(computed.deductions());
                payslip.setNetAmount(computed.net());
                payslip.setLopDays(computed.lopDays());
                payslip.setBasicEffective(computed.basicApplied());
                payslip.setStatus(PayslipStatus.DRAFT);
                Payslip savedSlip = payslips.save(payslip);
                for (PayslipLine line : computed.lines()) {
                    savedSlip.addLine(line);
                }

                Instant now = Instant.now();
                for (PayrollArrear a : pendingArrears) {
                    a.setPayrollRun(run);
                    a.setApplied(true);
                    a.setAppliedAt(now);
                }

                totalGross = totalGross.add(computed.gross());
                totalDeductions = totalDeductions.add(computed.deductions());
                totalNet = totalNet.add(computed.net());
                processed++;

                publishPayslip(PayrollEventType.PAYSLIP_GENERATED, savedSlip);
            }
        } catch (RuntimeException e) {
            run.setStatus(PayrollRunStatus.FAILED);
            run.setFailedAt(Instant.now());
            run.setFailureReason(e.getMessage());
            publishRun(PayrollEventType.RUN_FAILED, run);
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Payroll run failed: " + e.getMessage(), e);
        }

        run.setEmployeesProcessed(processed);
        run.setTotalGross(totalGross);
        run.setTotalDeductions(totalDeductions);
        run.setTotalNet(totalNet);
        run.setStatus(PayrollRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        publishRun(PayrollEventType.RUN_COMPLETED, run);
    }

    public PayrollRunResponse finalizeRun(UUID tenantId, UUID id) {
        PayrollRun run = require(tenantId, id);
        guard.requireAccessForCompany(run.getCompanyId());
        policy.assertFinalizable(run);
        UUID actor = requireActor();
        Instant now = Instant.now();
        for (Payslip p : payslips.findAllForRun(tenantId, run.getId())) {
            p.setStatus(PayslipStatus.FINALIZED);
            p.setFinalizedAt(now);
            publishPayslip(PayrollEventType.PAYSLIP_FINALIZED, p);
        }
        run.setStatus(PayrollRunStatus.FINALIZED);
        run.setFinalizedAt(now);
        run.setFinalizedBy(actor);
        publishRun(PayrollEventType.RUN_FINALIZED, run);
        return mapper.toResponse(run);
    }

    public PayrollRunResponse freeze(UUID tenantId, UUID id) {
        PayrollRun run = require(tenantId, id);
        guard.requireAccessForCompany(run.getCompanyId());
        policy.assertFreezable(run);
        UUID actor = requireActor();
        run.setStatus(PayrollRunStatus.FROZEN);
        run.setFrozenAt(Instant.now());
        run.setFrozenBy(actor);
        publishRun(PayrollEventType.RUN_FROZEN, run);
        return mapper.toResponse(run);
    }

    /** Stores a pre-run validation report onto the run row for audit. */
    public void recordValidationReport(PayrollRun run, PayrollValidationReport report) {
        try {
            run.setValidationReportJson(JSON.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            run.setValidationReportJson(null);
        }
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse getById(UUID tenantId, UUID id) {
        PayrollRun run = require(tenantId, id);
        guard.requireAccessForCompany(run.getCompanyId());
        return mapper.toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<PayrollRunResponse> forPeriod(UUID tenantId, UUID periodId) {
        List<PayrollRun> found = runs.findAllForPeriod(tenantId, periodId);
        guard.requireAccessForCompanies(found.stream().map(PayrollRun::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    /** Run history for a company across every period — "Payroll Run History" (Sprint 24J). */
    @Transactional(readOnly = true)
    public List<PayrollRunResponse> forCompany(
            UUID tenantId, UUID companyId, PayrollRunStatus status) {
        guard.requireAccessForCompany(companyId);
        List<PayrollRun> found =
                status == null
                        ? runs.findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(
                                tenantId, companyId)
                        : runs.findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
                                tenantId, companyId, status);
        return found.stream().map(mapper::toResponse).toList();
    }

    /**
     * Reconstructs a run's activity timeline purely from timestamp/actor fields already on the row
     * — no separate audit-log table, since every fact needed is already recorded there.
     */
    @Transactional(readOnly = true)
    public List<PayrollRunTimelineEventResponse> timeline(UUID tenantId, UUID id) {
        PayrollRun run = require(tenantId, id);
        guard.requireAccessForCompany(run.getCompanyId());
        List<PayrollRunTimelineEventResponse> events = new ArrayList<>();
        events.add(
                new PayrollRunTimelineEventResponse(
                        "CREATED",
                        run.getCreatedAt(),
                        run.getCreatedBy(),
                        run.getRunType().name()));
        if (run.getStartedAt() != null) {
            events.add(
                    new PayrollRunTimelineEventResponse(
                            "STARTED", run.getStartedAt(), run.getStartedBy(), null));
        }
        if (run.getCompletedAt() != null) {
            events.add(
                    new PayrollRunTimelineEventResponse(
                            "COMPLETED",
                            run.getCompletedAt(),
                            null,
                            run.getEmployeesProcessed() + " employees processed"));
        }
        if (run.getFailedAt() != null) {
            events.add(
                    new PayrollRunTimelineEventResponse(
                            "FAILED", run.getFailedAt(), null, run.getFailureReason()));
        }
        if (run.getFinalizedAt() != null) {
            events.add(
                    new PayrollRunTimelineEventResponse(
                            "FINALIZED", run.getFinalizedAt(), run.getFinalizedBy(), null));
        }
        if (run.getFrozenAt() != null) {
            events.add(
                    new PayrollRunTimelineEventResponse(
                            "FROZEN", run.getFrozenAt(), run.getFrozenBy(), null));
        }
        events.sort(
                Comparator.comparing(
                        PayrollRunTimelineEventResponse::occurredAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        return events;
    }

    private PayrollRun require(UUID tenantId, UUID id) {
        return runs.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payroll run not found"));
    }

    /**
     * Resolves this employee's employee-side PF/ESI/PT/LWF/TDS deduction amounts for the period.
     * Runs a throwaway preview compute (no statutory amounts) purely to get the gross/basic/HRA the
     * statutory calculation services need as their wage basis — the payslip actually persisted is
     * built from a second, final {@link PayrollCalculator#compute} call once these amounts are
     * known. Employees without an {@link EmployeePayrollProfile} get no statutory deductions: the
     * profile is where a company opts an employee into the statutory engine, so its absence is a
     * configuration gap for the company to close, not something to guess at here. Persists any
     * newly-created ESI contribution-period enrollment and the updated tax-declaration YTD
     * accumulators as a side effect, same as the rest of this method's bulk-resolved, per-run maps.
     */
    @SuppressWarnings("PMD.ExcessiveParameterList")
    private StatutoryAmounts resolveStatutoryAmounts(
            PayrollRun run,
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
        if (profile == null) {
            return StatutoryAmounts.zero();
        }

        ComputedPayslip preview = calculator.compute(comp, lopDays, workingDays, pendingArrears);
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
                        run.getTenantId(),
                        run.getCompanyId());
        if (esiResult.enrollmentIsNew() && esiResult.enrollment() != null) {
            EmployeeEsiEnrollment saved = esiEnrollments.save(esiResult.enrollment());
            esiEnrollmentByEmployee.put(emp.getId(), saved);
        }

        EmployeeTaxDeclaration declaration = taxDeclarationByEmployee.get(emp.getId());
        if (declaration == null) {
            declaration = new EmployeeTaxDeclaration();
            declaration.setTenantId(run.getTenantId());
            declaration.setCompanyId(run.getCompanyId());
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
        int monthsRemaining =
                com.ewos.payroll.domain.FiscalYear.monthsRemaining(period.getPeriodStart());
        BigDecimal oneTimeGross = oneTimeGross(preview);
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

        declaration.setYtdTaxableSalary(declaration.getYtdTaxableSalary().add(recurringGross));
        declaration.setYtdHraReceived(declaration.getYtdHraReceived().add(monthlyHraReceived));
        declaration.setYtdTdsDeducted(
                declaration.getYtdTdsDeducted().add(tdsResult.recurringTdsRecovery()));
        declaration.setYtdVariablePaymentTdsRecovered(
                declaration
                        .getYtdVariablePaymentTdsRecovered()
                        .add(tdsResult.incrementalTaxOnOneTimePayment()));
        declaration.setYtdProfessionalTaxPaid(
                declaration.getYtdProfessionalTaxPaid().add(ptAmount));
        EmployeeTaxDeclaration savedDeclaration = taxDeclarations.save(declaration);
        taxDeclarationByEmployee.put(emp.getId(), savedDeclaration);

        recordTdsAdjustments(run, emp, period, savedDeclaration, tdsResult);

        return new StatutoryAmounts(
                pfResult.employeeContribution(),
                esiResult.employeeContribution(),
                ptAmount,
                lwfResult.employeeContribution(),
                tdsResult.monthlyTdsRecovery());
    }

    /**
     * Sums this preview's one-time/variable EARNING lines (Sprint 24K §8.3) — a bonus/incentive
     * component explicitly flagged {@code recurring = false}, or an arrear line (which never
     * carries a {@link PayComponent} reference and is identified by its {@code "ARREAR_"} code
     * prefix instead). The implicit BASIC line also has no {@link PayComponent} reference but is
     * always recurring, so the absence of a component alone is not treated as a one-time signal.
     */
    private static BigDecimal oneTimeGross(ComputedPayslip preview) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayslipLine line : preview.lines()) {
            if (line.getKind() != PayComponentKind.EARNING) {
                continue;
            }
            if (isOneTimeLine(line)) {
                total = total.add(line.getAmount());
            }
        }
        return total;
    }

    private static boolean isOneTimeLine(PayslipLine line) {
        if (line.getPayComponent() != null) {
            return !line.getPayComponent().isRecurring();
        }
        String code = line.getComponentCodeSnapshot();
        return code != null && code.startsWith("ARREAR_");
    }

    /**
     * Persists the audit trail for any non-standard TDS recovery this period — a shortfall capped
     * against actual payable earnings (§8.2) or an incremental recovery from a one-time payment
     * (§8.3). No row is written when the period's recovery was the plain even share.
     */
    private void recordTdsAdjustments(
            PayrollRun run,
            Employee emp,
            PayrollPeriod period,
            EmployeeTaxDeclaration declaration,
            TdsResult tdsResult) {
        int periodMonth = period.getPeriodStart().getMonthValue();
        if (tdsResult.shortfallCarriedForward().signum() > 0) {
            TdsAdjustmentLog log = new TdsAdjustmentLog();
            log.setTenantId(run.getTenantId());
            log.setCompanyId(run.getCompanyId());
            log.setEmployee(emp);
            log.setPayrollRun(run);
            log.setPeriodMonth(periodMonth);
            log.setAdjustmentType(TdsAdjustmentType.SHORTFALL_CAP);
            log.setExpectedRecovery(
                    tdsResult.recurringTdsRecovery().add(tdsResult.shortfallCarriedForward()));
            log.setActualRecovery(tdsResult.recurringTdsRecovery());
            log.setShortfallAmount(tdsResult.shortfallCarriedForward());
            log.setCumulativeYtdShortfall(tdsResult.shortfallCarriedForward());
            log.setNotes(
                    "Recovery capped against actual payable earnings this period; shortfall"
                            + " redistributes across remaining months via the normal even-share"
                            + " recalculation.");
            tdsAdjustmentLogs.save(log);
        }
        if (tdsResult.incrementalTaxOnOneTimePayment().signum() > 0) {
            TdsAdjustmentLog log = new TdsAdjustmentLog();
            log.setTenantId(run.getTenantId());
            log.setCompanyId(run.getCompanyId());
            log.setEmployee(emp);
            log.setPayrollRun(run);
            log.setPeriodMonth(periodMonth);
            log.setAdjustmentType(TdsAdjustmentType.VARIABLE_PAYMENT_INCREMENTAL);
            log.setExpectedRecovery(BigDecimal.ZERO);
            log.setActualRecovery(tdsResult.incrementalTaxOnOneTimePayment());
            log.setShortfallAmount(BigDecimal.ZERO);
            log.setCumulativeYtdShortfall(declaration.getYtdVariablePaymentTdsRecovered());
            log.setNotes(
                    "Incremental tax recovered in full this period against a one-time/variable"
                            + " payment; future recurring recovery is unaffected.");
            tdsAdjustmentLogs.save(log);
        }
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
        StringBuilder sb = new StringBuilder();
        if (e.getFirstName() != null) {
            sb.append(e.getFirstName());
        }
        if (e.getLastName() != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(e.getLastName());
        }
        return sb.toString();
    }

    private void publishRun(PayrollEventType type, PayrollRun r) {
        events.publishEvent(
                new PayrollEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        null,
                        r.getPayrollPeriod() != null ? r.getPayrollPeriod().getId() : null,
                        r.getId(),
                        null,
                        null,
                        r.getTotalNet(),
                        currentActor(),
                        Instant.now()));
    }

    private void publishPayslip(PayrollEventType type, Payslip p) {
        events.publishEvent(
                new PayrollEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        null,
                        p.getPayrollPeriod() != null ? p.getPayrollPeriod().getId() : null,
                        p.getPayrollRun() != null ? p.getPayrollRun().getId() : null,
                        p.getId(),
                        p.getEmployee() != null ? p.getEmployee().getId() : null,
                        p.getNetAmount(),
                        currentActor(),
                        Instant.now()));
    }

    private static UUID requireActor() {
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
        }
        return actor;
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
