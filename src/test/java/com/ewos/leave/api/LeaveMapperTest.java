package com.ewos.leave.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.leave.api.dto.AllocationResponse;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.domain.LeaveAllocation;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.leave.domain.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaveMapperTest {

    private final LeaveMapper mapper = new LeaveMapper(new LeaveBalanceCalculator());

    @Test
    void leaveTypeMapsAllFields() {
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setTenantId(UUID.randomUUID());
        t.setCode("VACATION");
        t.setName("Vacation");
        t.setPaid(true);
        t.setAccrualDaysPerYear(new BigDecimal("20.00"));
        t.setMinNoticeDays(3);
        t.setActive(true);

        LeaveTypeResponse r = mapper.toResponse(t);
        assertThat(r.code()).isEqualTo("VACATION");
        assertThat(r.paid()).isTrue();
        assertThat(r.accrualDaysPerYear()).isEqualByComparingTo("20.00");
        assertThat(r.minNoticeDays()).isEqualTo(3);
    }

    @Test
    void allocationMapsAndCarriesTypeCode() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setCode("VACATION");

        LeaveAllocation a = new LeaveAllocation();
        a.setId(UUID.randomUUID());
        a.setTenantId(UUID.randomUUID());
        a.setCompanyId(UUID.randomUUID());
        a.setEmployee(e);
        a.setLeaveType(t);
        a.setYear(2026);
        a.setAllocatedDays(new BigDecimal("15.00"));

        AllocationResponse r = mapper.toResponse(a);
        assertThat(r.year()).isEqualTo(2026);
        assertThat(r.leaveTypeCode()).isEqualTo("VACATION");
        assertThat(r.allocatedDays()).isEqualByComparingTo("15.00");
    }

    @Test
    void balanceIncludesComputedAvailable() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setCode("SICK");

        LeaveBalance b = new LeaveBalance();
        b.setId(UUID.randomUUID());
        b.setTenantId(UUID.randomUUID());
        b.setCompanyId(UUID.randomUUID());
        b.setEmployee(e);
        b.setLeaveType(t);
        b.setYear(2026);
        b.setAccruedDays(new BigDecimal("10.00"));
        b.setConsumedDays(new BigDecimal("3.00"));
        b.setPendingDays(new BigDecimal("1.00"));

        BalanceResponse r = mapper.toResponse(b);
        assertThat(r.availableDays()).isEqualByComparingTo("6.00");
        assertThat(r.leaveTypeCode()).isEqualTo("SICK");
    }

    @Test
    void requestMapsAllStatusFields() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        LeaveType t = new LeaveType();
        t.setId(UUID.randomUUID());
        t.setCode("VACATION");

        LeaveRequest r = new LeaveRequest();
        r.setId(UUID.randomUUID());
        r.setTenantId(UUID.randomUUID());
        r.setCompanyId(UUID.randomUUID());
        r.setEmployee(e);
        r.setLeaveType(t);
        r.setStartDate(LocalDate.of(2026, 6, 1));
        r.setEndDate(LocalDate.of(2026, 6, 5));
        r.setDaysRequested(new BigDecimal("5"));
        r.setStatus(LeaveRequestStatus.SUBMITTED);
        r.setWorkflowInstanceId(UUID.randomUUID());

        LeaveRequestResponse resp = mapper.toResponse(r);
        assertThat(resp.status()).isEqualTo(LeaveRequestStatus.SUBMITTED);
        assertThat(resp.daysRequested()).isEqualByComparingTo("5");
        assertThat(resp.workflowInstanceId()).isNotNull();
    }
}
