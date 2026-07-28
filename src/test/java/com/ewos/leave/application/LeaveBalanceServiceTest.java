package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.LeaveMapper;
import com.ewos.leave.api.dto.AdjustBalanceRequest;
import com.ewos.leave.api.dto.UpsertAllocationRequest;
import com.ewos.leave.domain.LeaveAllocation;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveAllocationRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Leave allocation/balance bookkeeping: allocation upserts mirror into the running balance, and
 * adjustments accumulate rather than overwrite the adjustment bucket.
 */
@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceTest {

    @Mock LeaveAllocationRepository allocations;
    @Mock LeaveBalanceRepository balances;
    @Mock LeaveTypeService leaveTypes;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private LeaveBalanceService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID leaveTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new LeaveBalanceService(
                        allocations,
                        balances,
                        leaveTypes,
                        employees,
                        new LeaveMapper(new com.ewos.leave.domain.LeaveBalanceCalculator()),
                        events,
                        guard);
        org.mockito.Mockito.lenient()
                .when(allocations.save(any(LeaveAllocation.class)))
                .thenAnswer(
                        inv -> {
                            LeaveAllocation a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId(UUID.randomUUID());
                            }
                            return a;
                        });
        org.mockito.Mockito.lenient()
                .when(balances.save(any(LeaveBalance.class)))
                .thenAnswer(
                        inv -> {
                            LeaveBalance b = inv.getArgument(0);
                            if (b.getId() == null) {
                                b.setId(UUID.randomUUID());
                            }
                            return b;
                        });
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setTenantId(tenantId);
        e.setCompanyId(company);
        return e;
    }

    private LeaveType leaveType() {
        LeaveType t = new LeaveType();
        t.setId(leaveTypeId);
        return t;
    }

    private UpsertAllocationRequest allocationRequest(BigDecimal days) {
        return new UpsertAllocationRequest(
                tenantId, companyId, employeeId, leaveTypeId, 2026, days, null);
    }

    @Test
    void upsertAllocationRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.upsertAllocation(allocationRequest(new BigDecimal("18"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void upsertAllocationCreatesANewAllocationAndMirrorsItIntoTheBalance() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(leaveTypes.require(tenantId, leaveTypeId)).thenReturn(leaveType());
        when(allocations.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.empty());
        when(balances.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.empty());

        var response = service.upsertAllocation(allocationRequest(new BigDecimal("18")));

        assertThat(response.allocatedDays()).isEqualByComparingTo("18");
        org.mockito.ArgumentCaptor<LeaveBalance> captor =
                org.mockito.ArgumentCaptor.forClass(LeaveBalance.class);
        verify(balances).save(captor.capture());
        assertThat(captor.getValue().getAccruedDays()).isEqualByComparingTo("18");
    }

    @Test
    void upsertAllocationUpdatesAnExistingAllocationRatherThanDuplicatingIt() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(leaveTypes.require(tenantId, leaveTypeId)).thenReturn(leaveType());
        LeaveAllocation existing = new LeaveAllocation();
        existing.setId(UUID.randomUUID());
        existing.setAllocatedDays(new BigDecimal("12"));
        when(allocations.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.of(existing));
        LeaveBalance existingBalance = new LeaveBalance();
        existingBalance.setId(UUID.randomUUID());
        when(balances.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.of(existingBalance));

        var response = service.upsertAllocation(allocationRequest(new BigDecimal("21")));

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.allocatedDays()).isEqualByComparingTo("21");
        verify(balances, org.mockito.Mockito.never()).save(any());
        assertThat(existingBalance.getAccruedDays()).isEqualByComparingTo("21");
    }

    @Test
    void adjustRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        AdjustBalanceRequest req =
                new AdjustBalanceRequest(
                        tenantId,
                        companyId,
                        employeeId,
                        leaveTypeId,
                        2026,
                        new BigDecimal("2"),
                        null);

        assertThatThrownBy(() -> service.adjust(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void adjustAccumulatesDeltaDaysRatherThanOverwritingTheAdjustmentBucket() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(leaveTypes.require(tenantId, leaveTypeId)).thenReturn(leaveType());
        LeaveBalance existing = new LeaveBalance();
        existing.setId(UUID.randomUUID());
        existing.setAdjustmentDays(new BigDecimal("1.5"));
        when(balances.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.of(existing));

        AdjustBalanceRequest req =
                new AdjustBalanceRequest(
                        tenantId,
                        companyId,
                        employeeId,
                        leaveTypeId,
                        2026,
                        new BigDecimal("2.5"),
                        "Manual correction");
        var response = service.adjust(req);

        assertThat(response.adjustmentDays()).isEqualByComparingTo("4.0");
    }

    @Test
    void adjustAcceptsANegativeDeltaToDeductFromTheAdjustmentBucket() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        when(leaveTypes.require(tenantId, leaveTypeId)).thenReturn(leaveType());
        LeaveBalance existing = new LeaveBalance();
        existing.setId(UUID.randomUUID());
        existing.setAdjustmentDays(new BigDecimal("3"));
        when(balances.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, 2026))
                .thenReturn(Optional.of(existing));

        AdjustBalanceRequest req =
                new AdjustBalanceRequest(
                        tenantId,
                        companyId,
                        employeeId,
                        leaveTypeId,
                        2026,
                        new BigDecimal("-1"),
                        "Correcting an over-credit");
        var response = service.adjust(req);

        assertThat(response.adjustmentDays()).isEqualByComparingTo("2");
    }

    @Test
    void balancesForEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        LeaveBalance b = new LeaveBalance();
        b.setCompanyId(companyId);
        when(balances.findAllForEmployeeAndYear(tenantId, employeeId, 2026)).thenReturn(List.of(b));

        service.balancesForEmployee(tenantId, employeeId, 2026);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
