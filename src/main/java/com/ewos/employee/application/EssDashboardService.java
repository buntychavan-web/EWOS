package com.ewos.employee.application;

import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.api.dto.EssDashboardResponse;
import com.ewos.employee.api.dto.EssLeaveSummaryResponse;
import com.ewos.employee.api.dto.EssPayrollSnapshotResponse;
import com.ewos.employee.api.dto.EssPendingActionsResponse;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.application.LeaveBalanceService;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.notification.application.NotificationService;
import com.ewos.payroll.api.dto.PayslipLineResponse;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.application.PayslipService;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.StatutoryClassifier;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C — ESS Dashboard (PRD §4.1). A read-only aggregate over already-owned, already-tested
 * per-module reads; nothing here re-implements a module's own scoping/authorization, it only calls
 * into it (mirroring {@code ManagerApprovalsService}'s aggregator pattern from 27B).
 */
@Service
public class EssDashboardService {

    private static final int UPCOMING_EVENTS_DAYS = 30;
    private static final Set<String> TAX_STATUTORY_CODES = Set.of("INCOME_TAX", "TDS");

    private final EmployeeService employees;
    private final EmployeeContext employeeContext;
    private final NotificationService notifications;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveBalanceService leaveBalances;
    private final LeaveRequestService leaveRequestService;
    private final TimesheetRepository timesheets;
    private final PayslipService payslips;
    private final StatutoryClassifier statutoryClassifier;
    private final EssCalendarService calendar;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public EssDashboardService(
            EmployeeService employees,
            EmployeeContext employeeContext,
            NotificationService notifications,
            LeaveRequestRepository leaveRequests,
            LeaveBalanceService leaveBalances,
            LeaveRequestService leaveRequestService,
            TimesheetRepository timesheets,
            PayslipService payslips,
            StatutoryClassifier statutoryClassifier,
            EssCalendarService calendar) {
        this.employees = employees;
        this.employeeContext = employeeContext;
        this.notifications = notifications;
        this.leaveRequests = leaveRequests;
        this.leaveBalances = leaveBalances;
        this.leaveRequestService = leaveRequestService;
        this.timesheets = timesheets;
        this.payslips = payslips;
        this.statutoryClassifier = statutoryClassifier;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public EssDashboardResponse dashboard(UUID tenantId) {
        UUID employeeId =
                employeeContext
                        .currentEmployeeId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));

        LocalDate today = LocalDate.now();
        List<Timesheet> ownTimesheets = timesheets.findAllForEmployee(tenantId, employeeId);
        List<LeaveRequest> ownLeaveRequests =
                leaveRequests.findAllForEmployee(tenantId, employeeId);
        List<PayslipResponse> ownPayslips = payslips.forEmployee(tenantId, employeeId);

        return new EssDashboardResponse(
                employees.getById(tenantId, employeeId),
                pendingActions(tenantId, employeeId, ownTimesheets),
                leaveSummary(tenantId, employeeId, ownLeaveRequests, today),
                payrollSnapshot(ownPayslips, today.getYear()),
                calendar.upcoming(tenantId, employeeId, today, today.plusDays(UPCOMING_EVENTS_DAYS))
                        .events());
    }

    private EssPendingActionsResponse pendingActions(
            UUID tenantId, UUID employeeId, List<Timesheet> ownTimesheets) {
        List<Timesheet> open =
                ownTimesheets.stream().filter(t -> t.getStatus() == TimesheetStatus.DRAFT).toList();
        LocalDate nearestDeadline =
                open.stream()
                        .map(Timesheet::getPeriodEnd)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        return new EssPendingActionsResponse(
                notifications.unreadCount(tenantId),
                !open.isEmpty(),
                leaveRequestService.countPendingForManager(tenantId, employeeId),
                nearestDeadline);
    }

    private EssLeaveSummaryResponse leaveSummary(
            UUID tenantId, UUID employeeId, List<LeaveRequest> ownLeaveRequests, LocalDate today) {
        BigDecimal balanceDays =
                leaveBalances.balancesForEmployee(tenantId, employeeId, today.getYear()).stream()
                        .map(BalanceResponse::availableDays)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingRequests =
                ownLeaveRequests.stream()
                        .filter(r -> r.getStatus() == LeaveRequestStatus.SUBMITTED)
                        .count();
        LocalDate nextApprovedLeaveDate =
                ownLeaveRequests.stream()
                        .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                        .map(LeaveRequest::getStartDate)
                        .filter(start -> !start.isBefore(today))
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        return new EssLeaveSummaryResponse(balanceDays, pendingRequests, nextApprovedLeaveDate);
    }

    private EssPayrollSnapshotResponse payrollSnapshot(
            List<PayslipResponse> ownPayslips, int year) {
        if (ownPayslips.isEmpty()) {
            return new EssPayrollSnapshotResponse(null, null, null, null, null);
        }
        PayslipResponse latest = ownPayslips.get(0);
        List<PayslipResponse> ytd =
                ownPayslips.stream().filter(p -> p.periodStart().getYear() == year).toList();
        BigDecimal ytdGross =
                ytd.stream()
                        .map(PayslipResponse::grossAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ytdTax =
                ytd.stream()
                        .flatMap(p -> p.lines().stream())
                        .filter(this::isTaxDeductionLine)
                        .map(PayslipLineResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EssPayrollSnapshotResponse(
                latest.id(), latest.periodStart(), latest.periodEnd(), ytdGross, ytdTax);
    }

    private boolean isTaxDeductionLine(PayslipLineResponse line) {
        if (line.kind() != PayComponentKind.DEDUCTION) {
            return false;
        }
        return statutoryClassifier
                .classify(line.componentCode())
                .map(c -> TAX_STATUTORY_CODES.contains(c.code()))
                .orElse(false);
    }
}
