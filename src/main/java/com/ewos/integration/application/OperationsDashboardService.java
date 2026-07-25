package com.ewos.integration.application;

import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeRecordRepository;
import com.ewos.integration.api.dto.OperationsDashboardResponse;
import com.ewos.integration.api.dto.OperationsPipelineRowResponse;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 14.4 — Operations Dashboard. Composes Payroll, the Workflow Engine, Sprint 14.3's Data
 * Exchange, and Sprint 14.4's own Integration execution history into one pipeline view per Payroll
 * Run, without modifying or duplicating any of those modules' own state. The "one row per run"
 * shape mirrors {@code DataExchangeAutoCreateListener}'s convention that a finalized run produces
 * exactly one {@code PAYROLL_RUN_EXPORT} data exchange record under correlation id {@code
 * PAYROLL_RUN:<runId>}, and {@code PayrollApprovalWorkflowListener}'s convention that the client
 * approval workflow instance's subject is {@code (PAYROLL_RUN, runId)}.
 */
@Service
@Transactional(readOnly = true)
public class OperationsDashboardService {

    private static final String SUBJECT_TYPE = "PAYROLL_RUN";
    private static final int RUN_LIMIT = 50;

    private final PayrollRunRepository runs;
    private final WorkflowInstanceService workflowInstances;
    private final DataExchangeRecordRepository dataExchangeRecords;
    private final IntegrationExecutionRecordRepository executions;
    private final ClientAccessGuard guard;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public OperationsDashboardService(
            PayrollRunRepository runs,
            WorkflowInstanceService workflowInstances,
            DataExchangeRecordRepository dataExchangeRecords,
            IntegrationExecutionRecordRepository executions,
            ClientAccessGuard guard) {
        this.runs = runs;
        this.workflowInstances = workflowInstances;
        this.dataExchangeRecords = dataExchangeRecords;
        this.executions = executions;
        this.guard = guard;
    }

    public OperationsDashboardResponse forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        List<PayrollRun> companyRuns =
                runs.findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(tenantId, List.of(companyId));

        List<OperationsPipelineRowResponse> rows =
                companyRuns.stream().limit(RUN_LIMIT).map(run -> toRow(tenantId, run)).toList();

        return new OperationsDashboardResponse(companyId, rows);
    }

    private OperationsPipelineRowResponse toRow(UUID tenantId, PayrollRun run) {
        WorkflowInstanceResponse instance =
                workflowInstances.findBySubject(tenantId, SUBJECT_TYPE, run.getId()).stream()
                        .findFirst()
                        .orElse(null);

        DataExchangeRecord dataExchangeRecord =
                dataExchangeRecords
                        .findAllByTenantIdAndCorrelationIdOrderByCreatedAtDesc(
                                tenantId, SUBJECT_TYPE + ":" + run.getId())
                        .stream()
                        .findFirst()
                        .orElse(null);

        IntegrationExecutionRecord lastExecution =
                dataExchangeRecord == null
                        ? null
                        : executions
                                .findAllByDataExchangeRecordIdOrderByStartedAtDesc(dataExchangeRecord.getId())
                                .stream()
                                .findFirst()
                                .orElse(null);

        return new OperationsPipelineRowResponse(
                run.getId(),
                run.getCompanyId(),
                run.getStatus(),
                run.getCreatedAt(),
                instance == null ? null : instance.status(),
                instance == null ? null : instance.currentStateCode(),
                dataExchangeRecord == null ? null : dataExchangeRecord.getId(),
                dataExchangeRecord == null ? null : dataExchangeRecord.getStatus(),
                lastExecution == null ? null : lastExecution.getOutcome(),
                dataExchangeRecord != null && dataExchangeRecord.getAcknowledgedAt() != null);
    }
}
