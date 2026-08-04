package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollApprovalDecisionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecidePayrollApprovalRequest(
        @NotNull PayrollApprovalDecisionType decision, @Size(max = 2000) String comments) {}
