package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.api.dto.EmployeeResponse;
import com.ewos.employee.api.dto.EssCalendarResponse;
import com.ewos.employee.api.dto.EssDashboardResponse;
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
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.PayslipStatus;
import com.ewos.payroll.domain.StatutoryClassifier;
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

/** Sprint 27C — {@link EssDashboardService}: aggregation across every module it reads from. */
@ExtendWith(MockitoExtension.class)
class EssDashboardServiceTest {

    @Mock EmployeeService employees;
    @Mock EmployeeContext employeeContext;
    @Mock NotificationService notifications;
    @Mock LeaveRequestRepository leaveRequests;
    @Mock LeaveBalanceService leaveBalances;
    @Mock LeaveRequestService leaveRequestService;
    @Mock TimesheetRepository timesheets;
    @Mock PayslipService payslips;
    @Mock StatutoryClassifier statutoryClassifier;
    @Mock EssCalendarService calendar;

    private EssDashboardService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new EssDashboardService(
                        employees,
                        employeeContext,
                        notifications,
                        leaveRequests,
                        leaveBalances,
                        leaveRequestService,
                        timesheets,
                        payslips,
                        statutoryClassifier,
                        calendar);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(employees.getById(tenantId, employeeId))
                .thenReturn(org.mockito.Mockito.mock(EmployeeResponse.class));
        when(calendar.upcoming(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(employeeId),
                        any(),
                        any()))
                .thenReturn(new EssCalendarResponse(List.of()));
    }

    private static PayslipResponse payslip(
            UUID id,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal gross,
            List<PayslipLineResponse> lines) {
        return new PayslipResponse(
                id,
                null,
                null,
                null,
                null,
                null,
                "E1",
                "Jane Doe",
                periodStart,
                periodEnd,
                periodEnd,
                "INR",
                gross,
                BigDecimal.ZERO,
                gross,
                BigDecimal.ZERO,
                gross,
                PayslipStatus.FINALIZED,
                null,
                lines,
                0L);
    }

    @Test
    void timesheetDueIsTrueWhenAnOpenDraftTimesheetExistsAndReportsItsNearestDeadline() {
        Timesheet draft = new Timesheet();
        draft.setStatus(TimesheetStatus.DRAFT);
        draft.setPeriodEnd(LocalDate.of(2026, 8, 31));
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(draft));
        when(leaveRequests.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(payslips.forEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(leaveBalances.balancesForEmployee(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(employeeId),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(notifications.unreadCount(tenantId)).thenReturn(4L);
        when(leaveRequestService.countPendingForManager(tenantId, employeeId)).thenReturn(0L);

        EssDashboardResponse response = service.dashboard(tenantId);

        assertThat(response.pendingActions().notificationsUnread()).isEqualTo(4L);
        assertThat(response.pendingActions().timesheetDue()).isTrue();
        assertThat(response.pendingActions().upcomingTimesheetDeadline())
                .isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void leaveSummarySumsAvailableDaysAndFindsTheNearestFutureApprovedLeave() {
        LocalDate today = LocalDate.now();
        LeaveRequest pending = new LeaveRequest();
        pending.setStatus(LeaveRequestStatus.SUBMITTED);
        LeaveRequest approvedSoon = new LeaveRequest();
        approvedSoon.setStatus(LeaveRequestStatus.APPROVED);
        approvedSoon.setStartDate(today.plusDays(5));
        LeaveRequest approvedPast = new LeaveRequest();
        approvedPast.setStatus(LeaveRequestStatus.APPROVED);
        approvedPast.setStartDate(today.minusDays(5));
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(leaveRequests.findAllForEmployee(tenantId, employeeId))
                .thenReturn(List.of(pending, approvedSoon, approvedPast));
        when(payslips.forEmployee(tenantId, employeeId)).thenReturn(List.of());
        BalanceResponse b1 =
                new BalanceResponse(
                        null,
                        null,
                        null,
                        null,
                        null,
                        "AL",
                        today.getYear(),
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(6),
                        null,
                        null,
                        0L);
        BalanceResponse b2 =
                new BalanceResponse(
                        null,
                        null,
                        null,
                        null,
                        null,
                        "SL",
                        today.getYear(),
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.valueOf(2),
                        null,
                        null,
                        0L);
        when(leaveBalances.balancesForEmployee(tenantId, employeeId, today.getYear()))
                .thenReturn(List.of(b1, b2));
        when(notifications.unreadCount(tenantId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, employeeId)).thenReturn(0L);

        EssDashboardResponse response = service.dashboard(tenantId);

        assertThat(response.leaveSummary().balanceDays())
                .isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(response.leaveSummary().pendingRequests()).isEqualTo(1L);
        assertThat(response.leaveSummary().nextApprovedLeaveDate()).isEqualTo(today.plusDays(5));
    }

    @Test
    void payrollSnapshotIsAllNullWhenTheCallerHasNoPayslips() {
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(leaveRequests.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(payslips.forEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(leaveBalances.balancesForEmployee(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(employeeId),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(notifications.unreadCount(tenantId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, employeeId)).thenReturn(0L);

        EssDashboardResponse response = service.dashboard(tenantId);

        assertThat(response.payrollSnapshot().latestPayslipId()).isNull();
        assertThat(response.payrollSnapshot().ytdGross()).isNull();
        assertThat(response.payrollSnapshot().ytdTaxDeducted()).isNull();
    }

    @Test
    void payrollSnapshotSumsYtdGrossAndOnlyStatutoryTaxDeductionLinesWithinTheCurrentYear() {
        int thisYear = LocalDate.now().getYear();
        PayslipLineResponse taxLine =
                new PayslipLineResponse(
                        UUID.randomUUID(),
                        null,
                        "TDS",
                        "Tax Deducted at Source",
                        PayComponentKind.DEDUCTION,
                        PayComponentCalculationType.FIXED,
                        BigDecimal.valueOf(1000),
                        BigDecimal.ZERO,
                        1,
                        null);
        PayslipLineResponse loanLine =
                new PayslipLineResponse(
                        UUID.randomUUID(),
                        null,
                        "LOAN_REPAY",
                        "Loan Repayment",
                        PayComponentKind.DEDUCTION,
                        PayComponentCalculationType.FIXED,
                        BigDecimal.valueOf(500),
                        BigDecimal.ZERO,
                        2,
                        null);
        PayslipResponse thisYearPayslip =
                payslip(
                        UUID.randomUUID(),
                        LocalDate.of(thisYear, 1, 1),
                        LocalDate.of(thisYear, 1, 31),
                        BigDecimal.valueOf(50000),
                        List.of(taxLine, loanLine));
        PayslipResponse lastYearPayslip =
                payslip(
                        UUID.randomUUID(),
                        LocalDate.of(thisYear - 1, 12, 1),
                        LocalDate.of(thisYear - 1, 12, 31),
                        BigDecimal.valueOf(45000),
                        List.of(taxLine));
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(leaveRequests.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());
        when(payslips.forEmployee(tenantId, employeeId))
                .thenReturn(List.of(thisYearPayslip, lastYearPayslip));
        when(leaveBalances.balancesForEmployee(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(employeeId),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(notifications.unreadCount(tenantId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, employeeId)).thenReturn(0L);
        when(statutoryClassifier.classify("TDS"))
                .thenReturn(
                        Optional.of(new StatutoryClassifier.StatutoryClassification("IN", "TDS")));
        when(statutoryClassifier.classify("LOAN_REPAY")).thenReturn(Optional.empty());

        EssDashboardResponse response = service.dashboard(tenantId);

        assertThat(response.payrollSnapshot().latestPayslipId()).isEqualTo(thisYearPayslip.id());
        assertThat(response.payrollSnapshot().ytdGross())
                .isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(response.payrollSnapshot().ytdTaxDeducted())
                .isEqualByComparingTo(BigDecimal.valueOf(1000));
    }
}
