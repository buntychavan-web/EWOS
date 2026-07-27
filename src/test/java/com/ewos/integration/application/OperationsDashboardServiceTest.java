package com.ewos.integration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.ewos.dataexchange.infrastructure.persistence.DataExchangeRecordRepository;
import com.ewos.integration.api.dto.OperationsDashboardResponse;
import com.ewos.integration.api.dto.OperationsPipelineRowResponse;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import com.ewos.integration.infrastructure.persistence.IntegrationExecutionRecordRepository;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationsDashboardServiceTest {

    @Mock PayrollRunRepository runs;
    @Mock WorkflowInstanceService workflowInstances;
    @Mock DataExchangeRecordRepository dataExchangeRecords;
    @Mock IntegrationExecutionRecordRepository executions;
    @Mock ClientAccessGuard guard;

    private OperationsDashboardService service;

    @BeforeEach
    void setUp() {
        service =
                new OperationsDashboardService(
                        runs, workflowInstances, dataExchangeRecords, executions, guard);
    }

    private static PayrollRun run(UUID id, UUID companyId, PayrollRunStatus status) {
        PayrollRun r = new PayrollRun();
        r.setId(id);
        r.setCompanyId(companyId);
        r.setStatus(status);
        return r;
    }

    private static DataExchangeRecord dataExchangeRecord(UUID id, DataExchangeStatus status) {
        DataExchangeRecord r = new DataExchangeRecord();
        r.setId(id);
        r.setStatus(status);
        return r;
    }

    private static IntegrationExecutionRecord execution(IntegrationExecutionOutcome outcome) {
        IntegrationExecutionRecord r = new IntegrationExecutionRecord();
        r.setOutcome(outcome);
        return r;
    }

    @Test
    void buildsAFullPipelineRowWhenEveryStageHasData() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID dataExchangeId = UUID.randomUUID();
        when(runs.findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(tenantId, List.of(companyId)))
                .thenReturn(List.of(run(runId, companyId, PayrollRunStatus.FINALIZED)));
        WorkflowInstanceResponse instance =
                new WorkflowInstanceResponse(
                        UUID.randomUUID(),
                        tenantId,
                        companyId,
                        UUID.randomUUID(),
                        "PAYROLL_CLIENT_APPROVAL",
                        1,
                        "PAYROLL_RUN",
                        runId,
                        UUID.randomUUID(),
                        "APPROVED",
                        WorkflowInstanceStatus.COMPLETED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L);
        when(workflowInstances.findBySubject(tenantId, "PAYROLL_RUN", runId))
                .thenReturn(List.of(instance));
        DataExchangeRecord record =
                dataExchangeRecord(dataExchangeId, DataExchangeStatus.ACKNOWLEDGED);
        record.setAcknowledgedAt(java.time.Instant.now());
        when(dataExchangeRecords.findAllByTenantIdAndCorrelationIdOrderByCreatedAtDesc(
                        tenantId, "PAYROLL_RUN:" + runId))
                .thenReturn(List.of(record));
        when(executions.findAllByDataExchangeRecordIdOrderByStartedAtDesc(dataExchangeId))
                .thenReturn(List.of(execution(IntegrationExecutionOutcome.SUCCESS)));

        OperationsDashboardResponse dashboard = service.forCompany(tenantId, companyId);

        assertThat(dashboard.rows()).hasSize(1);
        OperationsPipelineRowResponse row = dashboard.rows().get(0);
        assertThat(row.payrollRunId()).isEqualTo(runId);
        assertThat(row.payrollRunStatus()).isEqualTo(PayrollRunStatus.FINALIZED);
        assertThat(row.clientApprovalInstanceStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
        assertThat(row.clientApprovalStateCode()).isEqualTo("APPROVED");
        assertThat(row.dataExchangeStatus()).isEqualTo(DataExchangeStatus.ACKNOWLEDGED);
        assertThat(row.lastIntegrationOutcome()).isEqualTo(IntegrationExecutionOutcome.SUCCESS);
        assertThat(row.acknowledged()).isTrue();
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void leavesDownstreamStagesNullWhenTheRunHasNoWorkflowOrDataExchangeYet() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        when(runs.findAllByTenantIdAndCompanyIdInOrderByCreatedAtDesc(tenantId, List.of(companyId)))
                .thenReturn(List.of(run(runId, companyId, PayrollRunStatus.PROCESSING)));
        when(workflowInstances.findBySubject(tenantId, "PAYROLL_RUN", runId)).thenReturn(List.of());
        when(dataExchangeRecords.findAllByTenantIdAndCorrelationIdOrderByCreatedAtDesc(
                        tenantId, "PAYROLL_RUN:" + runId))
                .thenReturn(List.of());

        OperationsDashboardResponse dashboard = service.forCompany(tenantId, companyId);

        OperationsPipelineRowResponse row = dashboard.rows().get(0);
        assertThat(row.clientApprovalInstanceStatus()).isNull();
        assertThat(row.dataExchangeStatus()).isNull();
        assertThat(row.lastIntegrationOutcome()).isNull();
        assertThat(row.acknowledged()).isFalse();
    }
}
