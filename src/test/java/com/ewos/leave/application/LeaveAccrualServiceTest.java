package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.leave.application.LeaveAccrualService.AccrualOutcome;
import com.ewos.leave.application.LeaveAccrualService.AccrualResult;
import com.ewos.leave.domain.LeaveAccrualEntry;
import com.ewos.leave.domain.LeaveAccrualEntry.EntryType;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.leave.domain.events.LeaveEventType;
import com.ewos.leave.infrastructure.persistence.LeaveAccrualEntryRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Sprint 27D, revised by the Sprint 27D reconciliation — {@link
 * LeaveAccrualService#accrueForEmployee}: pro-rata monthly crediting, daily pro-rata for an
 * employee's first eligible period, idempotency, hire-date eligibility, employee-status
 * eligibility, and {@code maxBalanceDays} capping.
 */
@ExtendWith(MockitoExtension.class)
class LeaveAccrualServiceTest {

    @Mock LeaveAccrualEntryRepository entries;
    @Mock LeaveBalanceRepository balances;
    @Mock LeaveBalanceService leaveBalanceService;
    @Mock LeaveBalanceCalculator calculator;
    @Mock ApplicationEventPublisher events;

    private LeaveAccrualService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final int year = 2026;
    private final int month = 8;

    @BeforeEach
    void setUp() {
        service =
                new LeaveAccrualService(entries, balances, leaveBalanceService, calculator, events);
    }

    private Employee employee(LocalDate hireDate) {
        return employee(hireDate, EmployeeStatus.ACTIVE);
    }

    private Employee employee(LocalDate hireDate, EmployeeStatus status) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        e.setHireDate(hireDate);
        e.setStatus(status);
        return e;
    }

    private LeaveType type(BigDecimal accrualDaysPerYear, BigDecimal maxBalanceDays) {
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setAccrualDaysPerYear(accrualDaysPerYear);
        t.setMaxBalanceDays(maxBalanceDays);
        return t;
    }

    private LeaveBalance balance(BigDecimal accruedDays) {
        LeaveBalance b = new LeaveBalance();
        b.setId(UUID.randomUUID());
        b.setAccruedDays(accruedDays);
        return b;
    }

    private void stubExistsForPeriod(Employee employee, LeaveType type, boolean exists) {
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(exists);
    }

    private void stubBalance(Employee employee, LeaveType type, LeaveBalance balance) {
        when(leaveBalanceService.getOrCreateBalance(employee, type, year, companyId))
                .thenReturn(balance);
    }

    // ------------------------------------------------------------------
    // Idempotency
    // ------------------------------------------------------------------

    @Test
    void alreadyProcessedPeriodIsSkippedEntirely() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, true);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.ALREADY_PROCESSED);
        verify(leaveBalanceService, never()).getOrCreateBalance(any(), any(), anyInt(), any());
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------
    // Hire-date eligibility
    // ------------------------------------------------------------------

    @Test
    void employeeHiredAfterThePeriodIsNotEligibleYet() {
        Employee employee = employee(LocalDate.of(2026, 9, 15));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_YET);
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void employeeWithNoHireDateIsNotEligibleYet() {
        Employee employee = employee(null);
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_YET);
    }

    // ------------------------------------------------------------------
    // Employee-status eligibility — approved Sprint 27D baseline decision 5
    // ------------------------------------------------------------------

    @Test
    void activeEmployeeAccrues() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), EmployeeStatus.ACTIVE);
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
    }

    @Test
    void onLeaveEmployeeStillAccruesBecauseTheyRemainEmployed() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), EmployeeStatus.ON_LEAVE);
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
        assertThat(result.creditedDays()).isEqualByComparingTo("2.00");
    }

    @Test
    void suspendedEmployeeDoesNotAccrue() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), EmployeeStatus.SUSPENDED);
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_STATUS);
        verify(leaveBalanceService, never()).getOrCreateBalance(any(), any(), anyInt(), any());
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void terminatedEmployeeDoesNotAccrue() {
        Employee employee = employee(LocalDate.of(2020, 1, 1), EmployeeStatus.TERMINATED);
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_STATUS);
        verify(entries, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Full-month accrual (hired well before the period)
    // ------------------------------------------------------------------

    @Test
    void creditsProRataMonthlyShareWhenNoCapConfigured() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        LeaveBalance balance = balance(BigDecimal.ZERO);
        stubBalance(employee, type, balance);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
        assertThat(result.requestedDays()).isEqualByComparingTo("2.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("2.00");
        assertThat(result.capped()).isFalse();
        assertThat(balance.getAccruedDays()).isEqualByComparingTo("2.00");
        verify(balances).save(balance);

        ArgumentCaptor<LeaveAccrualEntry> entryCaptor =
                ArgumentCaptor.forClass(LeaveAccrualEntry.class);
        verify(entries).save(entryCaptor.capture());
        LeaveAccrualEntry saved = entryCaptor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getEmployee()).isEqualTo(employee);
        assertThat(saved.getLeaveType()).isEqualTo(type);
        assertThat(saved.getLeaveBalance()).isEqualTo(balance);
        assertThat(saved.getAccrualYear()).isEqualTo(year);
        assertThat(saved.getAccrualMonth()).isEqualTo(month);
        assertThat(saved.getRequestedDays()).isEqualByComparingTo("2.00");
        assertThat(saved.getCreditedDays()).isEqualByComparingTo("2.00");
        assertThat(saved.isCapped()).isFalse();
        assertThat(saved.getEntryType()).isEqualTo(EntryType.MONTHLY_ACCRUAL);

        ArgumentCaptor<LeaveEvent> eventCaptor = ArgumentCaptor.forClass(LeaveEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(LeaveEventType.BALANCE_ACCRUED);
        assertThat(eventCaptor.getValue().days()).isEqualByComparingTo("2.00");
    }

    // ------------------------------------------------------------------
    // Daily pro-rata for the employee's first eligible period — decision 3
    // ------------------------------------------------------------------

    @Test
    void hiredOnFirstDayOfMonthIsEquivalentToTheFullMonthEntitlement() {
        // August 2026 has 31 days; hired on day 1 => 31/31 worked => full month, via pro-rata math.
        Employee employee = employee(LocalDate.of(2026, 8, 1));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
        assertThat(result.requestedDays()).isEqualByComparingTo("2.00");
    }

    @Test
    void hiredOnLastDayOfMonthGetsOnlyASingleDaysProRataShare() {
        // August 2026 has 31 days; hired on day 31 => 1/31 worked.
        // monthlyEntitlement = 24.00 / 12 = 2.00; 2.00 * 1 / 31 = 0.0645... -> 0.06 HALF_UP.
        Employee employee = employee(LocalDate.of(2026, 8, 31));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
        assertThat(result.requestedDays()).isEqualByComparingTo("0.06");
        assertThat(result.creditedDays()).isEqualByComparingTo("0.06");
    }

    @Test
    void hiredMidMonthGetsAPartialProRataShare() {
        // August 2026 has 31 days; hired on day 16 => 16 days worked (31 - 16 + 1).
        // 2.00 * 16 / 31 = 1.032... -> 1.03 HALF_UP.
        Employee employee = employee(LocalDate.of(2026, 8, 16));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false);
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.requestedDays()).isEqualByComparingTo("1.03");
        assertThat(result.creditedDays()).isEqualByComparingTo("1.03");
    }

    @Test
    void leapYearFebruaryProRataUsesTwentyNineDaysInTheDenominator() {
        // 2028 is a leap year; February has 29 days. Hired on day 15 => 15 days worked
        // (29 - 15 + 1). 2.00 * 15 / 29 = 1.0344... -> 1.03 HALF_UP.
        Employee employee = employee(LocalDate.of(2028, 2, 15));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), 2028, 2)).thenReturn(false);
        when(leaveBalanceService.getOrCreateBalance(employee, type, 2028, companyId))
                .thenReturn(balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, 2028, 2);

        assertThat(result.requestedDays()).isEqualByComparingTo("1.03");
    }

    @Test
    void nonLeapYearFebruaryProRataUsesTwentyEightDaysInTheDenominator() {
        // 2026 is not a leap year; February has 28 days. Hired on day 15 => 14 days worked
        // (28 - 15 + 1). 2.00 * 14 / 28 = 1.00 exactly.
        Employee employee = employee(LocalDate.of(2026, 2, 15));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), 2026, 2)).thenReturn(false);
        when(leaveBalanceService.getOrCreateBalance(employee, type, 2026, companyId))
                .thenReturn(balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, 2026, 2);

        assertThat(result.requestedDays()).isEqualByComparingTo("1.00");
    }

    @Test
    void secondEligibleMonthAfterAProRatedFirstMonthGetsTheFullEntitlement() {
        // hireDate is in a prior month relative to the period being accrued, so this call is NOT
        // the employee's first eligible period and must get the full monthly share, not pro-rata.
        Employee employee = employee(LocalDate.of(2026, 7, 20));
        LeaveType type = type(new BigDecimal("24.00"), null);
        stubExistsForPeriod(employee, type, false); // period is August (month=8)
        stubBalance(employee, type, balance(BigDecimal.ZERO));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.requestedDays()).isEqualByComparingTo("2.00");
    }

    // ------------------------------------------------------------------
    // Zero-entitlement protection
    // ------------------------------------------------------------------

    @Test
    void zeroAccrualDaysPerYearProducesZeroRequestedAndCreditedButStillRecordsTheEntry() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(BigDecimal.ZERO, null);
        stubExistsForPeriod(employee, type, false);
        LeaveBalance balance = balance(BigDecimal.ZERO);
        stubBalance(employee, type, balance);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
        assertThat(result.requestedDays()).isEqualByComparingTo("0.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("0.00");
        assertThat(result.capped()).isFalse();
        verify(balances, never()).save(any());
        verify(entries).save(any());
        verify(events, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------
    // maxBalanceDays capping — unchanged from PR #42's original implementation
    // ------------------------------------------------------------------

    @Test
    void capsAtMaxBalanceDaysWhenAccrualWouldExceedIt() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), new BigDecimal("10.00"));
        stubExistsForPeriod(employee, type, false);
        LeaveBalance balance = balance(new BigDecimal("9.00"));
        stubBalance(employee, type, balance);
        when(calculator.availableDays(balance)).thenReturn(new BigDecimal("9.00"));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.requestedDays()).isEqualByComparingTo("2.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("1.00");
        assertThat(result.capped()).isTrue();
        assertThat(balance.getAccruedDays()).isEqualByComparingTo("10.00");
    }

    @Test
    void doesNotCreditWhenFullyCappedButStillRecordsTheEntrySoThePeriodIsMarkedProcessed() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), new BigDecimal("10.00"));
        stubExistsForPeriod(employee, type, false);
        LeaveBalance balance = balance(new BigDecimal("10.00"));
        stubBalance(employee, type, balance);
        when(calculator.availableDays(balance)).thenReturn(new BigDecimal("10.00"));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.creditedDays()).isEqualByComparingTo("0.00");
        assertThat(result.capped()).isTrue();
        verify(balances, never()).save(any());
        verify(entries).save(any());
        verify(events, never()).publishEvent(any());
    }
}
