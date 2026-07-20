package com.ewos.leave.api;

import com.ewos.leave.api.dto.AllocationResponse;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.domain.LeaveAllocation;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveType;
import org.springframework.stereotype.Component;

@Component
public final class LeaveMapper {

    private final LeaveBalanceCalculator calculator;

    public LeaveMapper(LeaveBalanceCalculator calculator) {
        this.calculator = calculator;
    }

    public LeaveTypeResponse toResponse(LeaveType t) {
        return new LeaveTypeResponse(
                t.getId(),
                t.getTenantId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.isPaid(),
                t.getAccrualDaysPerYear(),
                t.getMaxBalanceDays(),
                t.getCarryForwardDays(),
                t.isRequiresApproval(),
                t.getMinNoticeDays(),
                t.isActive(),
                t.getSortOrder(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCreatedBy(),
                t.getUpdatedBy(),
                t.getVersionNo());
    }

    public AllocationResponse toResponse(LeaveAllocation a) {
        return new AllocationResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getEmployee() != null ? a.getEmployee().getId() : null,
                a.getLeaveType() != null ? a.getLeaveType().getId() : null,
                a.getLeaveType() != null ? a.getLeaveType().getCode() : null,
                a.getYear(),
                a.getAllocatedDays(),
                a.getNotes(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getCreatedBy(),
                a.getUpdatedBy(),
                a.getVersionNo());
    }

    public BalanceResponse toResponse(LeaveBalance b) {
        return new BalanceResponse(
                b.getId(),
                b.getTenantId(),
                b.getCompanyId(),
                b.getEmployee() != null ? b.getEmployee().getId() : null,
                b.getLeaveType() != null ? b.getLeaveType().getId() : null,
                b.getLeaveType() != null ? b.getLeaveType().getCode() : null,
                b.getYear(),
                b.getAccruedDays(),
                b.getConsumedDays(),
                b.getPendingDays(),
                b.getAdjustmentDays(),
                b.getCarryForwardDays(),
                calculator.availableDays(b),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersionNo());
    }

    public LeaveRequestResponse toResponse(LeaveRequest r) {
        return new LeaveRequestResponse(
                r.getId(),
                r.getTenantId(),
                r.getCompanyId(),
                r.getEmployee() != null ? r.getEmployee().getId() : null,
                r.getLeaveType() != null ? r.getLeaveType().getId() : null,
                r.getLeaveType() != null ? r.getLeaveType().getCode() : null,
                r.getStartDate(),
                r.getEndDate(),
                r.getDaysRequested(),
                r.getReason(),
                r.getStatus(),
                r.getSubmittedAt(),
                r.getApprovedAt(),
                r.getApprovedBy(),
                r.getRejectedAt(),
                r.getRejectedBy(),
                r.getRejectionReason(),
                r.getCancelledAt(),
                r.getCancelledBy(),
                r.getWorkflowInstanceId(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getUpdatedBy(),
                r.getVersionNo());
    }
}
