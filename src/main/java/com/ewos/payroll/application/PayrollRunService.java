package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.api.dto.StartPayrollRunRequest;
import com.ewos.payroll.domain.EmployeeCompensation;
import com.ewos.payroll.domain.LopCalculator;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.domain.PayrollCalculator;
import com.ewos.payroll.domain.PayrollCalculator.ComputedPayslip;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.domain.PayrollValidationReport;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipStatus;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.payroll.infrastructure.persistence.PayslipRepository;
import com.ewos.shared.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
            ApplicationEventPublisher events) {
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
    }

    public PayrollRunResponse start(StartPayrollRunRequest request) {
        PayrollPeriod period = periods.require(request.tenantId(), request.payrollPeriodId());
        if (!period.getCompanyId().equals(request.companyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Period belongs to a different company");
        }
        policy.assertRunnable(period);
        UUID actor = requireActor();

        PayrollRun run = new PayrollRun();
        run.setTenantId(request.tenantId());
        run.setCompanyId(request.companyId());
        run.setPayrollPeriod(period);
        run.setStatus(PayrollRunStatus.PENDING);
        PayrollRun saved = runs.save(run);
        publishRun(PayrollEventType.RUN_STARTED, saved);

        saved.setStatus(PayrollRunStatus.PROCESSING);
        saved.setStartedAt(Instant.now());
        saved.setStartedBy(actor);
        processPayslips(saved, period);
        return mapper.toResponse(saved);
    }

    private void processPayslips(PayrollRun run, PayrollPeriod period) {
        List<EmployeeCompensation> active =
                compensations.activeForCompany(run.getTenantId(), run.getCompanyId());

        BigDecimal workingDays =
                lop.weekdaysBetween(period.getPeriodStart(), period.getPeriodEnd());
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int processed = 0;

        try {
            for (EmployeeCompensation comp : active) {
                Employee emp = comp.getEmployee();
                if (emp == null) {
                    continue;
                }

                List<LeaveRequest> approvedInPeriod =
                        leaves.findApprovedOverlapping(
                                run.getTenantId(),
                                emp.getId(),
                                period.getPeriodStart(),
                                period.getPeriodEnd());
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
                        arrears.findPendingForEmployee(run.getTenantId(), emp.getId());

                ComputedPayslip computed =
                        calculator.compute(comp, lopDays, workingDays, pendingArrears);

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
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayrollRunResponse> forPeriod(UUID tenantId, UUID periodId) {
        return runs.findAllForPeriod(tenantId, periodId).stream().map(mapper::toResponse).toList();
    }

    private PayrollRun require(UUID tenantId, UUID id) {
        return runs.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payroll run not found"));
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
