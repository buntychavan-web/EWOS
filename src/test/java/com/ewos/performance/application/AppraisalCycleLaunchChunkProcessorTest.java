package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalCycleLaunchBatch;
import com.ewos.performance.domain.AppraisalCycleLaunchBatchStatus;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.infrastructure.persistence.AppraisalCycleLaunchBatchRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppraisalCycleLaunchChunkProcessorTest {

    @Mock AppraisalRepository appraisals;
    @Mock EmployeeRepository employees;
    @Mock AppraisalCycleLaunchBatchRepository batches;

    private AppraisalCycleLaunchChunkProcessor processor;
    private UUID tenantId;
    private UUID batchId;
    private PerformanceCycle cycle;
    private AppraisalTemplate template;

    @BeforeEach
    void setUp() {
        processor = new AppraisalCycleLaunchChunkProcessor(appraisals, employees, batches);
        tenantId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        cycle = new PerformanceCycle();
        cycle.setId(UUID.randomUUID());
        template = new AppraisalTemplate();
        template.setId(UUID.randomUUID());
    }

    private AppraisalCycleLaunchBatch batch() {
        AppraisalCycleLaunchBatch b = new AppraisalCycleLaunchBatch();
        b.setId(batchId);
        b.setTenantId(tenantId);
        b.setStatus(AppraisalCycleLaunchBatchStatus.RUNNING);
        return b;
    }

    private Employee employee(UUID id, UUID companyId) {
        Employee e = new Employee();
        e.setId(id);
        e.setCompanyId(companyId);
        return e;
    }

    @Test
    void skipsEmployeesWhoAlreadyHaveAnAppraisalInTheCycle() {
        UUID alreadyHas = UUID.randomUUID();
        UUID needsOne = UUID.randomUUID();
        AppraisalCycleLaunchBatch batch = batch();
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));
        when(appraisals.findAllByTenantIdAndCycleIdAndEmployeeIdIn(
                        tenantId, cycle.getId(), List.of(alreadyHas, needsOne)))
                .thenReturn(List.of(alreadyHas));
        when(employees.findByIdAndTenantId(needsOne, tenantId))
                .thenReturn(Optional.of(employee(needsOne, UUID.randomUUID())));

        processor.processChunk(tenantId, batchId, cycle, template, List.of(alreadyHas, needsOne));

        ArgumentCaptor<Appraisal> saved = ArgumentCaptor.forClass(Appraisal.class);
        verify(appraisals).save(saved.capture());
        assertThat(saved.getValue().getEmployee().getId()).isEqualTo(needsOne);
        assertThat(saved.getValue().getLaunchBatchId()).isEqualTo(batchId);
        assertThat(saved.getValue().getStatus()).isEqualTo(AppraisalStatus.PENDING_SELF);
        assertThat(batch.getTotalCreated()).isEqualTo(1);
        assertThat(batch.getTotalSkippedExisting()).isEqualTo(1);
        assertThat(batch.getTotalFailed()).isEqualTo(0);
    }

    @Test
    void countsAFailureWithoutAbortingTheRestOfTheChunk() {
        UUID vanished = UUID.randomUUID();
        UUID ok = UUID.randomUUID();
        AppraisalCycleLaunchBatch batch = batch();
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));
        when(appraisals.findAllByTenantIdAndCycleIdAndEmployeeIdIn(any(), any(), anyList()))
                .thenReturn(List.of());
        when(employees.findByIdAndTenantId(vanished, tenantId)).thenReturn(Optional.empty());
        when(employees.findByIdAndTenantId(ok, tenantId))
                .thenReturn(Optional.of(employee(ok, UUID.randomUUID())));

        processor.processChunk(tenantId, batchId, cycle, template, List.of(vanished, ok));

        assertThat(batch.getTotalCreated()).isEqualTo(1);
        assertThat(batch.getTotalFailed()).isEqualTo(1);
        verify(appraisals).save(any(Appraisal.class));
    }

    @Test
    void markRunningSetsStatusAndMatchedCount() {
        AppraisalCycleLaunchBatch batch = batch();
        batch.setStatus(AppraisalCycleLaunchBatchStatus.PENDING);
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        processor.markRunning(tenantId, batchId, 42);

        assertThat(batch.getStatus()).isEqualTo(AppraisalCycleLaunchBatchStatus.RUNNING);
        assertThat(batch.getTotalMatched()).isEqualTo(42);
        assertThat(batch.getStartedAt()).isNotNull();
    }

    @Test
    void markCompletedIsPartiallyCompletedWhenSomeEmployeesFailed() {
        AppraisalCycleLaunchBatch batch = batch();
        batch.setTotalFailed(3);
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        processor.markCompleted(tenantId, batchId);

        assertThat(batch.getStatus())
                .isEqualTo(AppraisalCycleLaunchBatchStatus.PARTIALLY_COMPLETED);
        assertThat(batch.getCompletedAt()).isNotNull();
    }

    @Test
    void markCompletedIsFullyCompletedWhenNothingFailed() {
        AppraisalCycleLaunchBatch batch = batch();
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        processor.markCompleted(tenantId, batchId);

        assertThat(batch.getStatus()).isEqualTo(AppraisalCycleLaunchBatchStatus.COMPLETED);
    }

    @Test
    void markFailedRecordsTheErrorMessage() {
        AppraisalCycleLaunchBatch batch = batch();
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        processor.markFailed(tenantId, batchId, "boom");

        assertThat(batch.getStatus()).isEqualTo(AppraisalCycleLaunchBatchStatus.FAILED);
        assertThat(batch.getErrorMessage()).isEqualTo("boom");
    }
}
