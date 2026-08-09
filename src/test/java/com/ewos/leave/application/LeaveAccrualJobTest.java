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
import com.ewos.leave.application.LeaveAccrualService.AccrualOutcome;
import com.ewos.leave.application.LeaveAccrualService.AccrualResult;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import java.math.BigDecimal;
import java.util.List;
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
 * Sprint 27D — {@link LeaveAccrualJob}'s sweep orchestration: disabled short-circuit, iterating
 * every accrual-eligible leave type against every active employee page, and delegating the actual
 * decision to {@link LeaveAccrualService}.
 */
@ExtendWith(MockitoExtension.class)
class LeaveAccrualJobTest {

    @Mock LeaveTypeRepository leaveTypes;
    @Mock EmployeeRepository employees;
    @Mock LeaveAccrualService accrualService;

    private LeaveAccrualJob job;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        job = new LeaveAccrualJob(leaveTypes, employees, accrualService);
        ReflectionTestUtils.setField(job, "batchSize", 2);
        ReflectionTestUtils.setField(job, "zone", "UTC");
    }

    private LeaveType type() {
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setAccrualDaysPerYear(new BigDecimal("24.00"));
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

        verify(leaveTypes, never()).findAllActiveWithAccrual(any());
        verify(employees, never()).findAllByTenantIdAndStatus(any(), any(), any());
        verify(accrualService, never()).accrueForEmployee(any(), any(), anyInt(), anyInt());
    }

    @Test
    void callsTheServiceOnceForEveryEmployeeOfEveryEligibleType() {
        ReflectionTestUtils.setField(job, "enabled", true);
        LeaveType type = type();
        when(leaveTypes.findAllActiveWithAccrual(any())).thenReturn(List.of(type));
        Employee e1 = employee();
        Employee e2 = employee();
        Page<Employee> onlyPage = new PageImpl<>(List.of(e1, e2), PageRequest.of(0, 2), 2);
        when(employees.findAllByTenantIdAndStatus(eq(tenantId), eq(EmployeeStatus.ACTIVE), any()))
                .thenReturn(onlyPage);
        when(accrualService.accrueForEmployee(any(), any(), anyInt(), anyInt()))
                .thenReturn(
                        new AccrualResult(
                                AccrualOutcome.CREDITED,
                                new BigDecimal("2.00"),
                                new BigDecimal("2.00"),
                                false));

        job.runAll();

        verify(accrualService, times(2)).accrueForEmployee(any(), eq(type), anyInt(), anyInt());
    }

    @Test
    void pagesThroughMoreThanOneBatchOfEmployees() {
        ReflectionTestUtils.setField(job, "enabled", true);
        LeaveType type = type();
        when(leaveTypes.findAllActiveWithAccrual(any())).thenReturn(List.of(type));
        Employee e1 = employee();
        Employee e2 = employee();
        Employee e3 = employee();
        Pageable firstPage = PageRequest.of(0, 2);
        Page<Employee> page1 = new PageImpl<>(List.of(e1, e2), firstPage, 3);
        Page<Employee> page2 = new PageImpl<>(List.of(e3), firstPage.next(), 3);
        when(employees.findAllByTenantIdAndStatus(
                        eq(tenantId), eq(EmployeeStatus.ACTIVE), eq(firstPage)))
                .thenReturn(page1);
        when(employees.findAllByTenantIdAndStatus(
                        eq(tenantId), eq(EmployeeStatus.ACTIVE), eq(firstPage.next())))
                .thenReturn(page2);
        when(accrualService.accrueForEmployee(any(), any(), anyInt(), anyInt()))
                .thenReturn(
                        new AccrualResult(
                                AccrualOutcome.CREDITED,
                                new BigDecimal("2.00"),
                                new BigDecimal("2.00"),
                                false));

        job.runAll();

        verify(accrualService, times(3)).accrueForEmployee(any(), eq(type), anyInt(), anyInt());
    }

    @Test
    void runNowBypassesTheScheduleButStillRespectsTheDisabledFlag() {
        ReflectionTestUtils.setField(job, "enabled", false);

        job.runNow();

        verify(leaveTypes, never()).findAllActiveWithAccrual(any());
    }
}
