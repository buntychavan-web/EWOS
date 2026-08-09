package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.application.LeaveAccrualService.AccrualOutcome;
import com.ewos.leave.application.LeaveAccrualService.AccrualResult;
import com.ewos.leave.domain.LeaveAccrualEntry;
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
 * Sprint 27D — {@link LeaveAccrualService#accrueForEmployee}: pro-rata monthly crediting,
 * idempotency, hire-date eligibility, and {@code maxBalanceDays} capping.
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
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        e.setHireDate(hireDate);
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

    @Test
    void alreadyProcessedPeriodIsSkippedEntirely() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month)).thenReturn(true);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.ALREADY_PROCESSED);
        verify(leaveBalanceService, never()).getOrCreateBalance(any(), any(), anyInt(), any());
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void employeeHiredAfterThePeriodIsNotEligibleYet() {
        Employee employee = employee(LocalDate.of(2026, 9, 15));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_YET);
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void employeeWithNoHireDateIsNotEligibleYet() {
        Employee employee = employee(null);
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.NOT_ELIGIBLE_YET);
    }

    @Test
    void hiredWithinTheAccrualMonthIsEligibleForThatMonth() {
        Employee employee = employee(LocalDate.of(2026, 8, 31));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);
        LeaveBalance balance = balance(BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, year, companyId))
                .thenReturn(balance);

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.outcome()).isEqualTo(AccrualOutcome.CREDITED);
    }

    @Test
    void creditsProRataMonthlyShareWhenNoCapConfigured() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), null);
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);
        LeaveBalance balance = balance(BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, year, companyId))
                .thenReturn(balance);

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

        ArgumentCaptor<LeaveEvent> eventCaptor = ArgumentCaptor.forClass(LeaveEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(LeaveEventType.BALANCE_ACCRUED);
        assertThat(eventCaptor.getValue().days()).isEqualByComparingTo("2.00");
    }

    @Test
    void capsAtMaxBalanceDaysWhenAccrualWouldExceedIt() {
        Employee employee = employee(LocalDate.of(2020, 1, 1));
        LeaveType type = type(new BigDecimal("24.00"), new BigDecimal("10.00"));
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);
        LeaveBalance balance = balance(new BigDecimal("9.00"));
        when(leaveBalanceService.getOrCreateBalance(employee, type, year, companyId))
                .thenReturn(balance);
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
        when(entries.existsForPeriod(employee.getId(), type.getId(), year, month))
                .thenReturn(false);
        LeaveBalance balance = balance(new BigDecimal("10.00"));
        when(leaveBalanceService.getOrCreateBalance(employee, type, year, companyId))
                .thenReturn(balance);
        when(calculator.availableDays(balance)).thenReturn(new BigDecimal("10.00"));

        AccrualResult result = service.accrueForEmployee(employee, type, year, month);

        assertThat(result.creditedDays()).isEqualByComparingTo("0.00");
        assertThat(result.capped()).isTrue();
        verify(balances, never()).save(any());
        verify(entries).save(any());
        verify(events, never()).publishEvent(any());
    }
}
