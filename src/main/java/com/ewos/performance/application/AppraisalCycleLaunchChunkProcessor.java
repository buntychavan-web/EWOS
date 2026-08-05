package com.ewos.performance.application;

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
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24B — per-chunk work for {@code AppraisalCycleLaunchRunner}. Each call is its own,
 * independent {@code REQUIRES_NEW} transaction so a launch across 100k+ employees commits progress
 * incrementally (a crash mid-run leaves every already-committed chunk's appraisals intact — nothing
 * to roll back or redo) instead of holding one multi-minute transaction open for the whole job.
 */
@Service
public class AppraisalCycleLaunchChunkProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(AppraisalCycleLaunchChunkProcessor.class);

    private final AppraisalRepository appraisals;
    private final EmployeeRepository employees;
    private final AppraisalCycleLaunchBatchRepository batches;

    public AppraisalCycleLaunchChunkProcessor(
            AppraisalRepository appraisals,
            EmployeeRepository employees,
            AppraisalCycleLaunchBatchRepository batches) {
        this.appraisals = appraisals;
        this.employees = employees;
        this.batches = batches;
    }

    /**
     * Creates appraisals for whichever ids in {@code candidateEmployeeIds} don't already have one
     * in {@code cycle} — the per-employee idempotency guarantee: re-running an overlapping launch
     * (accidentally or deliberately) only ever fills the gap, courtesy of {@code
     * ux_appraisals_cycle_employee_alive}. Any single employee failing to build/save (e.g. an id
     * that no longer resolves) is counted and skipped rather than aborting the rest of the chunk.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processChunk(
            UUID tenantId,
            UUID batchId,
            PerformanceCycle cycle,
            AppraisalTemplate template,
            List<UUID> candidateEmployeeIds) {
        AppraisalCycleLaunchBatch batch =
                batches.findByIdAndTenantId(batchId, tenantId)
                        .orElseThrow(
                                () -> new IllegalStateException("Launch batch vanished mid-run"));

        Set<UUID> alreadyHaveAppraisal =
                new HashSet<>(
                        appraisals.findAllByTenantIdAndCycleIdAndEmployeeIdIn(
                                tenantId, cycle.getId(), candidateEmployeeIds));

        // Batched once for the whole chunk instead of once per employee — at chunks of up to
        // 2,000 ids, the per-employee round trip this replaced was the dominant cost of a launch
        // across 100k+ employees (same fix shape as PayrollRunService's bulk leave/arrear
        // fetches, Sprint 18).
        Map<UUID, Employee> employeesById =
                employees.findAllByIdInAndTenantId(candidateEmployeeIds, tenantId).stream()
                        .collect(Collectors.toMap(Employee::getId, e -> e));

        int created = 0;
        int failed = 0;
        for (UUID employeeId : candidateEmployeeIds) {
            if (alreadyHaveAppraisal.contains(employeeId)) {
                continue;
            }
            try {
                Employee employee = employeesById.get(employeeId);
                if (employee == null) {
                    throw new IllegalStateException("Employee vanished mid-run");
                }
                Appraisal a = new Appraisal();
                a.setTenantId(tenantId);
                a.setCompanyId(employee.getCompanyId());
                a.setCycle(cycle);
                a.setTemplate(template);
                a.setEmployee(employee);
                a.setManagerEmployee(employee.getManager());
                a.setStatus(AppraisalStatus.PENDING_SELF);
                a.setLaunchBatchId(batchId);
                appraisals.save(a);
                created++;
            } catch (RuntimeException e) {
                failed++;
                log.warn(
                        "Bulk launch batch {} failed to open appraisal for employee {}: {}",
                        batchId,
                        employeeId,
                        e.getMessage());
            }
        }

        int skipped = alreadyHaveAppraisal.size();
        batch.setTotalCreated(batch.getTotalCreated() + created);
        batch.setTotalSkippedExisting(batch.getTotalSkippedExisting() + skipped);
        batch.setTotalFailed(batch.getTotalFailed() + failed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID tenantId, UUID batchId, int totalMatched) {
        AppraisalCycleLaunchBatch batch = require(tenantId, batchId);
        batch.setStatus(AppraisalCycleLaunchBatchStatus.RUNNING);
        batch.setTotalMatched(totalMatched);
        batch.setStartedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID tenantId, UUID batchId) {
        AppraisalCycleLaunchBatch batch = require(tenantId, batchId);
        batch.setStatus(
                batch.getTotalFailed() > 0
                        ? AppraisalCycleLaunchBatchStatus.PARTIALLY_COMPLETED
                        : AppraisalCycleLaunchBatchStatus.COMPLETED);
        batch.setCompletedAt(Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID tenantId, UUID batchId, String errorMessage) {
        AppraisalCycleLaunchBatch batch = require(tenantId, batchId);
        batch.setStatus(AppraisalCycleLaunchBatchStatus.FAILED);
        batch.setErrorMessage(errorMessage);
        batch.setCompletedAt(Instant.now());
    }

    private AppraisalCycleLaunchBatch require(UUID tenantId, UUID batchId) {
        return batches.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Launch batch vanished mid-run"));
    }
}
