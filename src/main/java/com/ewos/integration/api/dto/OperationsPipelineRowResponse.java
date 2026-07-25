package com.ewos.integration.api.dto;

import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 14.4 — Operations Dashboard. One row per Payroll Run, showing where it stands across the
 * whole pipeline: Payroll → Client Approval → Data Exchange → Integration → Acknowledgement. Built
 * entirely by reading each module's own existing state (Payroll's {@code PayrollRun}, the
 * Workflow Engine's instance for that run, Sprint 14.3's {@code DataExchangeRecord}, and Sprint
 * 14.4's own {@code IntegrationExecutionRecord}) — no new persistence, no cross-module writes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationsPipelineRowResponse(
        UUID payrollRunId,
        UUID companyId,
        PayrollRunStatus payrollRunStatus,
        Instant payrollRunCreatedAt,
        WorkflowInstanceStatus clientApprovalInstanceStatus,
        String clientApprovalStateCode,
        UUID dataExchangeRecordId,
        DataExchangeStatus dataExchangeStatus,
        IntegrationExecutionOutcome lastIntegrationOutcome,
        boolean acknowledged) {}
