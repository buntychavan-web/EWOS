package com.ewos.performance.application;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sprint 24B — the async worker behind bulk appraisal-cycle launch. Fires only after {@code
 * AppraisalCycleLaunchService.launch()}'s transaction commits (never races the batch row it's about
 * to load), resolves the full eligible-employee set in one query, then hands it to {@link
 * AppraisalCycleLaunchChunkProcessor} in bounded chunks so no single transaction — or single query
 * result the JPA persistence context has to track — ever covers the whole run.
 */
@Component
public class AppraisalCycleLaunchRunner {

    private static final Logger log = LoggerFactory.getLogger(AppraisalCycleLaunchRunner.class);
    private static final int CHUNK_SIZE = 2000;

    private final AppraisalCycleLaunchBatchRepository batches;
    private final EmployeeRepository employees;
    private final OrganizationUnitRepository organizationUnits;
    private final AppraisalCycleLaunchChunkProcessor chunkProcessor;
    private final ApplicationEventPublisher events;

    public AppraisalCycleLaunchRunner(
            AppraisalCycleLaunchBatchRepository batches,
            EmployeeRepository employees,
            OrganizationUnitRepository organizationUnits,
            AppraisalCycleLaunchChunkProcessor chunkProcessor,
            ApplicationEventPublisher events) {
        this.batches = batches;
        this.employees = employees;
        this.organizationUnits = organizationUnits;
        this.chunkProcessor = chunkProcessor;
        this.events = events;
    }

    @Async("bulkOperationsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLaunchRequested(AppraisalCycleLaunchRequested event) {
        UUID tenantId = event.tenantId();
        UUID batchId = event.batchId();
        try {
            run(tenantId, batchId);
        } catch (RuntimeException e) {
            log.error("Bulk launch batch {} failed", batchId, e);
            chunkProcessor.markFailed(tenantId, batchId, e.getMessage());
        }
    }

    private void run(UUID tenantId, UUID batchId) {
        AppraisalCycleLaunchBatch batch =
                batches.findByIdAndTenantIdWithCycleAndTemplate(batchId, tenantId)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Launch batch not found: " + batchId));
        PerformanceCycle cycle = batch.getCycle();
        AppraisalTemplate template = batch.getTemplate();

        List<UUID> orgUnitFilter = resolveOrgUnitFilter(tenantId, batch);
        EmployeeStatus status = EmployeeStatus.valueOf(batch.getFilterEmployeeStatus());
        List<UUID> eligible =
                employees.findEligibleEmployeeIds(
                        tenantId,
                        batch.getCompanyId(),
                        status,
                        orgUnitFilter,
                        batch.getFilterEmploymentTypeId());

        chunkProcessor.markRunning(tenantId, batchId, eligible.size());

        for (List<UUID> chunk : partition(eligible, CHUNK_SIZE)) {
            chunkProcessor.processChunk(tenantId, batchId, cycle, template, chunk);
        }

        chunkProcessor.markCompleted(tenantId, batchId);
        publishCompletionEvent(tenantId, batchId, cycle, template);
    }

    /**
     * {@code null} (no filter — every eligible employee) and an empty list (filter to nobody) are
     * different, load-bearing outcomes for {@code EmployeeRepository.findEligibleEmployeeIds}; this
     * intentionally does not follow the usual "return empty rather than null" convention.
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private List<UUID> resolveOrgUnitFilter(UUID tenantId, AppraisalCycleLaunchBatch batch) {
        if (batch.getFilterOrgUnitIds() == null || batch.getFilterOrgUnitIds().isBlank()) {
            return null;
        }
        List<UUID> roots =
                Arrays.stream(batch.getFilterOrgUnitIds().split(","))
                        .map(UUID::fromString)
                        .toList();
        return batch.isFilterIncludeDescendants()
                ? organizationUnits.findSelfAndDescendantIds(tenantId, roots)
                : roots;
    }

    private void publishCompletionEvent(
            UUID tenantId, UUID batchId, PerformanceCycle cycle, AppraisalTemplate template) {
        AppraisalCycleLaunchBatch finalState =
                batches.findByIdAndTenantId(batchId, tenantId).orElse(null);
        if (finalState == null) {
            return;
        }
        String detail =
                "Matched "
                        + finalState.getTotalMatched()
                        + ", created "
                        + finalState.getTotalCreated()
                        + ", skipped (already existed) "
                        + finalState.getTotalSkippedExisting()
                        + ", failed "
                        + finalState.getTotalFailed();
        events.publishEvent(
                new PerformanceEvent(
                        PerformanceEventType.CYCLE_LAUNCH_COMPLETED,
                        tenantId,
                        finalState.getCompanyId(),
                        cycle.getId(),
                        template.getId(),
                        null,
                        null,
                        null,
                        null,
                        detail,
                        finalState.getCreatedBy(),
                        Instant.now()));
    }

    private static List<List<UUID>> partition(List<UUID> ids, int size) {
        List<List<UUID>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += size) {
            chunks.add(ids.subList(i, Math.min(i + size, ids.size())));
        }
        return chunks;
    }
}
