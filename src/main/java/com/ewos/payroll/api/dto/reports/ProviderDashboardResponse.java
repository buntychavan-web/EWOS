package com.ewos.payroll.api.dto.reports;

import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.PayrollRunResponse;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.tenancy.api.dto.ClientResponse;
import java.util.List;
import java.util.Map;

/**
 * Sprint 14.2 — Provider Dashboard. Every list here is already scoped to the caller's accessible
 * clients by {@link com.ewos.tenancy.application.ClientAccessGuard} before this response is built,
 * so no field needs a separate per-row authorization check by the frontend.
 *
 * <p>"Pending Approvals" and "Service Status" are read directly off existing lifecycle state — a
 * {@code COMPLETED} run awaiting {@code finalizeRun()}, and the Service Catalogue's {@code active}
 * flag — rather than a new workflow or a Client Service Assignment concept, neither of which is in
 * scope for this sprint.
 */
public record ProviderDashboardResponse(
        List<ClientResponse> assignedClients,
        List<PayrollPeriodResponse> activePayrollPeriods,
        Map<PayrollRunStatus, Long> payrollStatusCounts,
        List<PayrollRunResponse> pendingApprovals,
        List<PayrollPeriodResponse> payrollCalendar,
        long activeServiceCount,
        long totalServiceCount) {}
