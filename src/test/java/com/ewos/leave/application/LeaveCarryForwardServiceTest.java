package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardOutcome;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardResult;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Sprint 27D reconciliation — {@link LeaveCarryForwardService#carryForwardForEmployee}: leftover
 * balance computation, {@code LeaveType.carryForwardDays} capping, {@code maxBalanceDays} capping
 * on the destination year, idempotency, and the decision-12 guarantee that carry-forward never
 * touches {@code adjustmentDays}.
 */
@ExtendWith(MockitoExtension.class)
class LeaveCarryForwardServiceTest {

    @Mock LeaveAccrualEntryRepository entries;
    @Mock LeaveBalanceRepository balances;
    @Mock LeaveBalanceService leaveBalanceService;
    @Mock LeaveBalanceCalculator calculator;
    @Mock ApplicationEventPublisher events;

    private LeaveCarryForwardService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final int fromYear = 2026;
    private final int toYear = 2027;

    @BeforeEach
    void setUp() {
        service =
                new LeaveCarryForwardService(
                        entries, balances, leaveBalanceService, calculator, events);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        return e;
    }

    private LeaveType type(BigDecimal carryForwardDays, BigDecimal maxBalanceDays) {
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setCarryForwardDays(carryForwardDays);
        t.setMaxBalanceDays(maxBalanceDays);
        return t;
    }

    private LeaveBalance sourceBalance() {
        LeaveBalance b = new LeaveBalance();
        b.setId(UUID.randomUUID());
        return b;
    }

    private LeaveBalance destinationBalance(
            BigDecimal carryForwardDays, BigDecimal adjustmentDays) {
        LeaveBalance b = new LeaveBalance();
        b.setId(UUID.randomUUID());
        b.setCarryForwardDays(carryForwardDays);
        b.setAdjustmentDays(adjustmentDays);
        return b;
    }

    @Test
    void alreadyProcessedYearIsSkippedEntirely() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("5.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(true);

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        assertThat(result.outcome()).isEqualTo(CarryForwardOutcome.ALREADY_PROCESSED);
        verify(balances, never()).findByEmployeeTypeYear(any(), any(), any(), anyInt());
        verify(leaveBalanceService, never()).getOrCreateBalance(any(), any(), anyInt(), any());
        verify(entries, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void carriesForwardTheFullLeftoverWhenBelowTheTypesCap() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("10.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        LeaveBalance source = sourceBalance();
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.of(source));
        when(calculator.availableDays(source)).thenReturn(new BigDecimal("4.50"));
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        assertThat(result.outcome()).isEqualTo(CarryForwardOutcome.CREDITED);
        assertThat(result.requestedDays()).isEqualByComparingTo("4.50");
        assertThat(result.creditedDays()).isEqualByComparingTo("4.50");
        assertThat(result.capped()).isFalse();
        assertThat(destination.getCarryForwardDays()).isEqualByComparingTo("4.50");
        verify(balances).save(destination);

        ArgumentCaptor<LeaveAccrualEntry> captor = ArgumentCaptor.forClass(LeaveAccrualEntry.class);
        verify(entries).save(captor.capture());
        LeaveAccrualEntry saved = captor.getValue();
        assertThat(saved.getEntryType()).isEqualTo(EntryType.CARRY_FORWARD);
        assertThat(saved.getAccrualYear()).isEqualTo(toYear);
        assertThat(saved.getAccrualMonth()).isEqualTo(1);
        assertThat(saved.getRequestedDays()).isEqualByComparingTo("4.50");
        assertThat(saved.getCreditedDays()).isEqualByComparingTo("4.50");
        assertThat(saved.isCapped()).isFalse();

        ArgumentCaptor<LeaveEvent> eventCaptor = ArgumentCaptor.forClass(LeaveEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(LeaveEventType.CARRY_FORWARD_APPLIED);
    }

    @Test
    void carryForwardIsCappedAtTheLeaveTypesCarryForwardDays() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("5.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        LeaveBalance source = sourceBalance();
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.of(source));
        when(calculator.availableDays(source)).thenReturn(new BigDecimal("12.00"));
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        // 12.00 available but the type only allows 5.00 to carry forward at all.
        assertThat(result.requestedDays()).isEqualByComparingTo("5.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("5.00");
        assertThat(destination.getCarryForwardDays()).isEqualByComparingTo("5.00");
    }

    @Test
    void carryForwardIsAlsoCappedByTheDestinationYearsMaxBalanceDays() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("10.00"), new BigDecimal("8.00"));
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        LeaveBalance source = sourceBalance();
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.of(source));
        when(calculator.availableDays(source)).thenReturn(new BigDecimal("10.00"));
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);
        // Destination year already has 3.00 accrued before carry-forward runs.
        when(calculator.availableDays(destination)).thenReturn(new BigDecimal("3.00"));

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        // type.carryForwardDays (10.00) doesn't limit it, but maxBalanceDays headroom
        // (8.00 - 3.00 = 5.00) does.
        assertThat(result.requestedDays()).isEqualByComparingTo("10.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("5.00");
        assertThat(result.capped()).isTrue();
        assertThat(destination.getCarryForwardDays()).isEqualByComparingTo("5.00");
    }

    @Test
    void noSourceYearBalanceTreatsLeftoverAsZeroButStillRecordsTheEntry() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("10.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.empty());
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        assertThat(result.requestedDays()).isEqualByComparingTo("0.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("0.00");
        verify(balances, never()).save(any());
        verify(entries).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void negativeAvailableSourceBalanceIsClampedToZeroLeftover() {
        // Pathological (pending+consumed exceeding accrued) but must never carry forward a
        // negative amount.
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("10.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        LeaveBalance source = sourceBalance();
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.of(source));
        when(calculator.availableDays(source)).thenReturn(new BigDecimal("-2.00"));
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, BigDecimal.ZERO);
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);

        CarryForwardResult result = service.carryForwardForEmployee(employee, type, fromYear);

        assertThat(result.requestedDays()).isEqualByComparingTo("0.00");
        assertThat(result.creditedDays()).isEqualByComparingTo("0.00");
    }

    @Test
    void neverModifiesTheDestinationBalancesAdjustmentDays() {
        Employee employee = employee();
        LeaveType type = type(new BigDecimal("10.00"), null);
        when(entries.existsCarryForwardForYear(employee.getId(), type.getId(), toYear))
                .thenReturn(false);
        LeaveBalance source = sourceBalance();
        when(balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), fromYear))
                .thenReturn(Optional.of(source));
        when(calculator.availableDays(source)).thenReturn(new BigDecimal("3.00"));
        LeaveBalance destination = destinationBalance(BigDecimal.ZERO, new BigDecimal("1.50"));
        when(leaveBalanceService.getOrCreateBalance(employee, type, toYear, companyId))
                .thenReturn(destination);

        service.carryForwardForEmployee(employee, type, fromYear);

        assertThat(destination.getAdjustmentDays()).isEqualByComparingTo("1.50");
    }
}
