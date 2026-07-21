package com.ewos.payroll.api.dto;

import java.util.List;
import java.util.UUID;

public record PayrollValidationReportResponse(
        UUID tenantId,
        UUID companyId,
        UUID payrollPeriodId,
        boolean runnable,
        int employeeCount,
        List<ValidationIssueResponse> blockers,
        List<ValidationIssueResponse> warnings) {}
