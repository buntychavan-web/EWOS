package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.performance.domain.AppraisalCycleLaunchBatch;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.events.AppraisalCycleLaunchRequested;
import com.ewos.performance.domain.events.PerformanceEvent;
import com.ewos.performance.domain.events.PerformanceEventType;
import com.ewos.performance.infrastructure.persistence.AppraisalCycleLaunchBatchRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AppraisalCycleLaunchRunnerTest {

    @Mock AppraisalCycleLaunchBatchRepository batches;
    @Mock EmployeeRepository employees;
    @Mock OrganizationUnitRepository organizationUnits;
    @Mock AppraisalCycleLaunchChunkProcessor chunkProcessor;
    @Mock ApplicationEventPublisher events;

    private AppraisalCycleLaunchRunner runner;
    private UUID tenantId;
    private UUID batchId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        runner =
                new AppraisalCycleLaunchRunner(
                        batches, employees, organizationUnits, chunkProcessor, events);
        tenantId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    private AppraisalCycleLaunchBatch batchWithCycleAndTemplate(String orgUnitFilter) {
        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setId(UUID.randomUUID());
        AppraisalTemplate template = new AppraisalTemplate();
        template.setId(UUID.randomUUID());

        AppraisalCycleLaunchBatch b = new AppraisalCycleLaunchBatch();
        b.setId(batchId);
        b.setTenantId(tenantId);
        b.setCompanyId(companyId);
        b.setCycle(cycle);
        b.setTemplate(template);
        b.setFilterEmployeeStatus(EmployeeStatus.ACTIVE.name());
        b.setFilterOrgUnitIds(orgUnitFilter);
        b.setFilterIncludeDescendants(true);
        return b;
    }

    @Test
    void resolvesEligibleEmployeesAndProcessesThemInChunks() {
        AppraisalCycleLaunchBatch batch = batchWithCycleAndTemplate(null);
        when(batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId))
                .thenReturn(Optional.of(batch));
        List<UUID> eligible =
                IntStream.range(0, 5).mapToObj(i -> UUID.randomUUID()).collect(Collectors.toList());
        when(employees.findEligibleEmployeeIds(
                        tenantId, companyId, EmployeeStatus.ACTIVE, null, null))
                .thenReturn(eligible);
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        runner.onLaunchRequested(new AppraisalCycleLaunchRequested(tenantId, batchId));

        verify(chunkProcessor).markRunning(tenantId, batchId, 5);
        verify(chunkProcessor)
                .processChunk(
                        eq(tenantId),
                        eq(batchId),
                        eq(batch.getCycle()),
                        eq(batch.getTemplate()),
                        eq(eligible));
        verify(chunkProcessor).markCompleted(tenantId, batchId);

        ArgumentCaptor<PerformanceEvent> eventCaptor =
                ArgumentCaptor.forClass(PerformanceEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(PerformanceEventType.CYCLE_LAUNCH_COMPLETED);
    }

    @Test
    void expandsOrgUnitFilterToItsDescendantsWhenRequested() {
        UUID root = UUID.randomUUID();
        AppraisalCycleLaunchBatch batch = batchWithCycleAndTemplate(root.toString());
        when(batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId))
                .thenReturn(Optional.of(batch));
        List<UUID> descendants = List.of(root, UUID.randomUUID());
        when(organizationUnits.findSelfAndDescendantIds(tenantId, List.of(root)))
                .thenReturn(descendants);
        when(employees.findEligibleEmployeeIds(
                        tenantId, companyId, EmployeeStatus.ACTIVE, descendants, null))
                .thenReturn(List.of());
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        runner.onLaunchRequested(new AppraisalCycleLaunchRequested(tenantId, batchId));

        verify(organizationUnits).findSelfAndDescendantIds(tenantId, List.of(root));
        verify(chunkProcessor).markRunning(tenantId, batchId, 0);
    }

    @Test
    void doesNotExpandDescendantsWhenTheBatchOptedOut() {
        UUID root = UUID.randomUUID();
        AppraisalCycleLaunchBatch batch = batchWithCycleAndTemplate(root.toString());
        batch.setFilterIncludeDescendants(false);
        when(batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId))
                .thenReturn(Optional.of(batch));
        when(employees.findEligibleEmployeeIds(
                        tenantId, companyId, EmployeeStatus.ACTIVE, List.of(root), null))
                .thenReturn(List.of());
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        runner.onLaunchRequested(new AppraisalCycleLaunchRequested(tenantId, batchId));

        verify(organizationUnits, never()).findSelfAndDescendantIds(any(), any());
    }

    @Test
    void marksTheBatchFailedInsteadOfPropagatingWhenSomethingBlowsUp() {
        when(batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId))
                .thenReturn(Optional.empty());

        runner.onLaunchRequested(new AppraisalCycleLaunchRequested(tenantId, batchId));

        verify(chunkProcessor).markFailed(eq(tenantId), eq(batchId), any());
        verify(chunkProcessor, never()).markCompleted(any(), any());
    }

    @Test
    void splitsALargeEligibleListIntoMultipleChunksOfTwoThousand() {
        AppraisalCycleLaunchBatch batch = batchWithCycleAndTemplate(null);
        when(batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId))
                .thenReturn(Optional.of(batch));
        List<UUID> eligible = new ArrayList<>();
        for (int i = 0; i < 4500; i++) {
            eligible.add(UUID.randomUUID());
        }
        when(employees.findEligibleEmployeeIds(any(), any(), any(), any(), any()))
                .thenReturn(eligible);
        when(batches.findByIdAndTenantId(batchId, tenantId)).thenReturn(Optional.of(batch));

        runner.onLaunchRequested(new AppraisalCycleLaunchRequested(tenantId, batchId));

        verify(chunkProcessor, times(3)).processChunk(any(), any(), any(), any(), any());
    }
}
