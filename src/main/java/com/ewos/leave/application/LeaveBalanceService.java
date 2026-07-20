package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.LeaveMapper;
import com.ewos.leave.api.dto.AdjustBalanceRequest;
import com.ewos.leave.api.dto.AllocationResponse;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.UpsertAllocationRequest;
import com.ewos.leave.domain.LeaveAllocation;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.leave.domain.events.LeaveEventType;
import com.ewos.leave.infrastructure.persistence.LeaveAllocationRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeaveBalanceService {

    private final LeaveAllocationRepository allocations;
    private final LeaveBalanceRepository balances;
    private final LeaveTypeService leaveTypes;
    private final EmployeeRepository employees;
    private final LeaveMapper mapper;
    private final ApplicationEventPublisher events;

    public LeaveBalanceService(
            LeaveAllocationRepository allocations,
            LeaveBalanceRepository balances,
            LeaveTypeService leaveTypes,
            EmployeeRepository employees,
            LeaveMapper mapper,
            ApplicationEventPublisher events) {
        this.allocations = allocations;
        this.balances = balances;
        this.leaveTypes = leaveTypes;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    /** Creates or updates the yearly allocation and mirrors it into the running balance. */
    public AllocationResponse upsertAllocation(UpsertAllocationRequest request) {
        Employee employee = requireEmployee(request.tenantId(), request.employeeId());
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        LeaveType type = leaveTypes.require(request.tenantId(), request.leaveTypeId());

        LeaveAllocation allocation =
                allocations
                        .findByEmployeeTypeYear(
                                request.tenantId(),
                                request.employeeId(),
                                request.leaveTypeId(),
                                request.year())
                        .orElseGet(
                                () -> {
                                    LeaveAllocation a = new LeaveAllocation();
                                    a.setTenantId(request.tenantId());
                                    a.setCompanyId(request.companyId());
                                    a.setEmployee(employee);
                                    a.setLeaveType(type);
                                    a.setYear(request.year());
                                    return a;
                                });
        allocation.setAllocatedDays(request.allocatedDays());
        if (request.notes() != null) {
            allocation.setNotes(request.notes());
        }
        LeaveAllocation saved = allocations.save(allocation);

        LeaveBalance balance =
                getOrCreateBalance(employee, type, request.year(), request.companyId());
        balance.setAccruedDays(request.allocatedDays());

        publish(
                LeaveEventType.ALLOCATION_CHANGED,
                employee,
                type,
                null,
                null,
                null,
                request.allocatedDays());
        return mapper.toResponse(saved);
    }

    public BalanceResponse adjust(AdjustBalanceRequest request) {
        Employee employee = requireEmployee(request.tenantId(), request.employeeId());
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        LeaveType type = leaveTypes.require(request.tenantId(), request.leaveTypeId());
        LeaveBalance balance =
                getOrCreateBalance(employee, type, request.year(), request.companyId());
        balance.setAdjustmentDays(balance.getAdjustmentDays().add(request.deltaDays()));

        publish(
                LeaveEventType.BALANCE_ADJUSTED,
                employee,
                type,
                null,
                null,
                null,
                request.deltaDays());
        return mapper.toResponse(balance);
    }

    @Transactional(readOnly = true)
    public List<BalanceResponse> balancesForEmployee(UUID tenantId, UUID employeeId, int year) {
        return balances.findAllForEmployeeAndYear(tenantId, employeeId, year).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AllocationResponse> allocationsForEmployee(
            UUID tenantId, UUID employeeId, int year) {
        return allocations.findAllForEmployeeAndYear(tenantId, employeeId, year).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Package-private helper used by {@link LeaveRequestService} when moving days between pending
     * and consumed buckets.
     */
    LeaveBalance requireBalanceForType(
            UUID tenantId, UUID employeeId, UUID leaveTypeId, int year, UUID companyId) {
        return balances.findByEmployeeTypeYear(tenantId, employeeId, leaveTypeId, year)
                .orElseGet(
                        () -> {
                            Employee e = requireEmployee(tenantId, employeeId);
                            LeaveType t = leaveTypes.require(tenantId, leaveTypeId);
                            return getOrCreateBalance(e, t, year, companyId);
                        });
    }

    LeaveBalance getOrCreateBalance(Employee employee, LeaveType type, int year, UUID companyId) {
        return balances.findByEmployeeTypeYear(
                        employee.getTenantId(), employee.getId(), type.getId(), year)
                .orElseGet(
                        () -> {
                            LeaveBalance b = new LeaveBalance();
                            b.setTenantId(employee.getTenantId());
                            b.setCompanyId(companyId);
                            b.setEmployee(employee);
                            b.setLeaveType(type);
                            b.setYear(year);
                            return balances.save(b);
                        });
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private void publish(
            LeaveEventType type,
            Employee employee,
            LeaveType leaveType,
            UUID leaveRequestId,
            java.time.LocalDate start,
            java.time.LocalDate end,
            BigDecimal days) {
        events.publishEvent(
                new LeaveEvent(
                        type,
                        employee.getTenantId(),
                        employee.getCompanyId(),
                        employee.getId(),
                        leaveType != null ? leaveType.getId() : null,
                        leaveRequestId,
                        start,
                        end,
                        days,
                        null,
                        currentActor(),
                        Instant.now()));
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
