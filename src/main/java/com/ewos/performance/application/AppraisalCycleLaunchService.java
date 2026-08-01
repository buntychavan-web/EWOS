package com.ewos.performance.application;

import com.ewos.performance.api.dto.AppraisalCycleLaunchBatchResponse;
import com.ewos.performance.api.dto.LaunchAppraisalCycleRequest;
import com.ewos.performance.domain.AppraisalCycleLaunchBatch;
import com.ewos.performance.domain.AppraisalCycleLaunchBatchStatus;
import com.ewos.performance.domain.AppraisalLifecyclePolicy;
import com.ewos.performance.domain.AppraisalTemplate;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.events.AppraisalCycleLaunchRequested;
import com.ewos.performance.infrastructure.persistence.AppraisalCycleLaunchBatchRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24B — public API for bulk appraisal-cycle launch. {@link #launch} does only synchronous,
 * fast work (validate, resolve the company/cycle/template, create the {@code PENDING} batch row)
 * and returns immediately; the actual employee-matching and appraisal creation happens
 * asynchronously in {@link AppraisalCycleLaunchRunner} after this method's transaction commits, so
 * an HTTP client launching a cycle for 100k+ employees gets a fast, pollable batch id rather than a
 * request that blocks for minutes.
 */
@Service
@Transactional
public class AppraisalCycleLaunchService {

    private final AppraisalCycleLaunchBatchRepository batches;
    private final PerformanceCycleService cycles;
    private final AppraisalTemplateService templates;
    private final AppraisalLifecyclePolicy lifecycle;
    private final ApplicationEventPublisher events;
    private final ClientAccessGuard guard;

    public AppraisalCycleLaunchService(
            AppraisalCycleLaunchBatchRepository batches,
            PerformanceCycleService cycles,
            AppraisalTemplateService templates,
            AppraisalLifecyclePolicy lifecycle,
            ApplicationEventPublisher events,
            ClientAccessGuard guard) {
        this.batches = batches;
        this.cycles = cycles;
        this.templates = templates;
        this.lifecycle = lifecycle;
        this.events = events;
        this.guard = guard;
    }

    public AppraisalCycleLaunchBatchResponse launch(UUID cycleId, LaunchAppraisalCycleRequest req) {
        guard.requireAccessForCompany(req.companyId());
        PerformanceCycle cycle = cycles.require(req.tenantId(), cycleId);
        AppraisalTemplate template = templates.require(req.tenantId(), req.templateId());
        lifecycle.assertOpenable(cycle, template);

        if (batches.existsByTenantIdAndCycleIdAndStatusIn(
                req.tenantId(),
                cycleId,
                List.of(
                        AppraisalCycleLaunchBatchStatus.PENDING,
                        AppraisalCycleLaunchBatchStatus.RUNNING))) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "A launch is already pending or running for this cycle");
        }

        AppraisalCycleLaunchBatch batch = new AppraisalCycleLaunchBatch();
        batch.setTenantId(req.tenantId());
        batch.setCompanyId(req.companyId());
        batch.setCycle(cycle);
        batch.setTemplate(template);
        batch.setStatus(AppraisalCycleLaunchBatchStatus.PENDING);
        batch.setFilterOrgUnitIds(
                req.organizationUnitIds() == null
                        ? null
                        : req.organizationUnitIds().stream()
                                .map(UUID::toString)
                                .collect(Collectors.joining(",")));
        batch.setFilterIncludeDescendants(req.resolvedIncludeDescendants());
        batch.setFilterEmploymentTypeId(req.employmentTypeId());
        batch.setFilterEmployeeStatus(req.resolvedEmployeeStatus().name());
        batch = batches.save(batch);

        events.publishEvent(new AppraisalCycleLaunchRequested(req.tenantId(), batch.getId()));
        return toResponse(batch);
    }

    @Transactional(readOnly = true)
    public AppraisalCycleLaunchBatchResponse getBatch(UUID tenantId, UUID cycleId, UUID batchId) {
        AppraisalCycleLaunchBatch batch =
                batches.findByIdAndTenantId(batchId, tenantId)
                        .filter(b -> b.getCycle().getId().equals(cycleId))
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Launch batch not found"));
        guard.requireAccessForCompany(batch.getCompanyId());
        return toResponse(batch);
    }

    @Transactional(readOnly = true)
    public List<AppraisalCycleLaunchBatchResponse> listForCycle(UUID tenantId, UUID cycleId) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());
        return batches.findAllByTenantIdAndCycleIdOrderByCreatedAtDesc(tenantId, cycleId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AppraisalCycleLaunchBatchResponse toResponse(AppraisalCycleLaunchBatch b) {
        return new AppraisalCycleLaunchBatchResponse(
                b.getId(),
                b.getTenantId(),
                b.getCompanyId(),
                b.getCycle().getId(),
                b.getTemplate().getId(),
                b.getStatus(),
                b.getTotalMatched(),
                b.getTotalCreated(),
                b.getTotalSkippedExisting(),
                b.getTotalFailed(),
                b.getErrorMessage(),
                b.getStartedAt(),
                b.getCompletedAt(),
                b.getCreatedAt());
    }
}
