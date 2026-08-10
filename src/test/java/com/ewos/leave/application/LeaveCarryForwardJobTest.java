package com.ewos.leave.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardOutcome;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardResult;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Sprint 27D reconciliation — {@link LeaveCarryForwardJob}'s sweep orchestration: disabled
 * short-circuit, iterating every carry-forward-eligible leave type against every ACTIVE-or-ON_LEAVE
 * employee page, delegating to {@link LeaveCarryForwardService}, and never double-crediting on
 * repeated (idempotent) runs.
 */
@ExtendWith(MockitoExtension.class)
class LeaveCarryForwardJobTest {

    private static final Set<EmployeeStatus> ACCRUING_STATUSES =
            EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE);

    @Mock LeaveTypeRepository leaveTypes;
    @Mock EmployeeRepository employees;
    @Mock LeaveCarryForwardService carryForwardService;

    private LeaveCarryForwardJob job;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        job = new LeaveCarryForwardJob(leaveTypes, employees, carryForwardService);
        ReflectionTestUtils.setField(job, "batchSize", 2);
        ReflectionTestUtils.setField(job, "zone", "UTC");
    }

    private LeaveType type() {
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setCarryForwardDays(new BigDecimal("5.00"));
        return t;
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        return e;
    }

    @Test
    void doesNothingWhenDisabled() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runAll();

        verify(leaveTypes, never()).findAllActiveWithCarryForward(any());
        verify(employees, never()).findAllByTenantIdAndStatusIn(any(), any(), any());
        verify(carryForwardService, never()).carryForwardForEmployee(any(), any(), anyInt());
    }

    @Test
    void callsTheServiceOnceForEveryEmployeeOfEveryEligibleType() {
        ReflectionTestUtils.setField(job, "enabled", true);
        LeaveType type = type();
        when(leaveTypes.findAllActiveWithCarryForward(any())).thenReturn(List.of(type));
        Employee e1 = employee();
        Employee e2 = employee();
        Page<Employee> onlyPage = new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 2), 2);
        when(employees.findAllByTenantIdAndStatusIn(eq(tenantId), eq(ACCRUING_STATUSES), any()))
                .thenReturn(onlyPage);
        when(carryForwardService.carryForwardForEmployee(any(), any(), anyInt()))
                .thenReturn(
                        new CarryForwardResult(
                                CarryForwardOutcome.CREDITED,
                                new BigDecimal("3.00"),
                                new BigDecimal("3.00"),
                                false));

        job.runAll();

        verify(carryForwardService, times(2)).carryForwardForEmployee(any(), eq(type), anyInt());
    }

    @Test
    void pagesThroughMoreThanOneBatchOfEmployees() {
        ReflectionTestUtils.setField(job, "enabled", true);
        LeaveType type = type();
        when(leaveTypes.findAllActiveWithCarryForward(any())).thenReturn(List.of(type));
        Employee e1 = employee();
        Employee e2 = employee();
        Employee e3 = employee();
        Pageable firstPage = PageRequest.of(0, 2);
        Page<Employee> page1 = new PageImpl<>(List.of(e1, e2), firstPage, 3);
        Page<Employee> page2 = new PageImpl<>(List.of(e3), firstPage.next(), 3);
        when(employees.findAllByTenantIdAndStatusIn(
                        eq(tenantId), eq(ACCRUING_STATUSES), eq(firstPage)))
                .thenReturn(page1);
        when(employees.findAllByTenantIdAndStatusIn(
                        eq(tenantId), eq(ACCRUING_STATUSES), eq(firstPage.next())))
                .thenReturn(page2);
        when(carryForwardService.carryForwardForEmployee(any(), any(), anyInt()))
                .thenReturn(
                        new CarryForwardResult(
                                CarryForwardOutcome.CREDITED,
                                new BigDecimal("3.00"),
                                new BigDecimal("3.00"),
                                false));

        job.runAll();

        verify(carryForwardService, times(3)).carryForwardForEmployee(any(), eq(type), anyInt());
    }

    /**
     * Sprint 27D reconciliation — the sweep itself has no idempotency logic; it always calls the
     * service, which is where duplicate-run protection actually lives (decision 7/13, verified in
     * {@code LeaveCarryForwardServiceTest#alreadyProcessedYearIsSkippedEntirely}). This just proves
     * the job survives an ALREADY_PROCESSED outcome from a re-run without throwing or
     * short-circuiting the rest of the sweep.
     */
    @Test
    void survivesAnAlreadyProcessedOutcomeOnRepeatedRuns() {
        ReflectionTestUtils.setField(job, "enabled", true);
        LeaveType type = type();
        when(leaveTypes.findAllActiveWithCarryForward(any())).thenReturn(List.of(type));
        Employee employee = employee();
        Page<Employee> onlyPage = new PageImpl<>(List.of(employee), PageRequest.of(0, 2), 1);
        when(employees.findAllByTenantIdAndStatusIn(eq(tenantId), eq(ACCRUING_STATUSES), any()))
                .thenReturn(onlyPage);
        when(carryForwardService.carryForwardForEmployee(any(), any(), anyInt()))
                .thenReturn(alreadyProcessed());

        job.runAll();
        job.runAll();

        verify(carryForwardService, times(2))
                .carryForwardForEmployee(eq(employee), eq(type), anyInt());
    }

    @Test
    void runNowBypassesTheScheduleButStillRespectsTheDisabledFlag() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runNow();

        verify(leaveTypes, never()).findAllActiveWithCarryForward(any());
    }

    private static CarryForwardResult alreadyProcessed() {
        return new CarryForwardResult(
                CarryForwardOutcome.ALREADY_PROCESSED, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }
}
