package com.ewos.performance.api;

import com.ewos.performance.api.dto.AppraisalCycleLaunchBatchResponse;
import com.ewos.performance.api.dto.LaunchAppraisalCycleRequest;
import com.ewos.performance.application.AppraisalCycleLaunchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24B — bulk appraisal-cycle launch. Gated {@code PERF_ADMIN} (not {@code PERF_WRITE}):
 * launching can create tens of thousands of appraisals in one call, a materially bigger blast
 * radius than opening one, so it sits at the same tier as template/cycle administration.
 */
@RestController
@RequestMapping("/api/v1/performance-cycles/{cycleId}/launch-batches")
@Tag(
        name = "Appraisal Cycle Bulk Launch",
        description = "Launch appraisals for many employees at once")
public class AppraisalCycleLaunchController {

    private final AppraisalCycleLaunchService launches;

    public AppraisalCycleLaunchController(AppraisalCycleLaunchService launches) {
        this.launches = launches;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERF_ADMIN')")
    @Operation(
            summary = "Launch a cycle for every matching employee",
            description =
                    "Resolves and creates appraisals asynchronously; returns immediately with a"
                            + " pollable batch id.")
    public ResponseEntity<AppraisalCycleLaunchBatchResponse> launch(
            @PathVariable UUID cycleId, @Valid @RequestBody LaunchAppraisalCycleRequest req) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(launches.launch(cycleId, req));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Launch-batch history for a cycle")
    public List<AppraisalCycleLaunchBatchResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID cycleId) {
        return launches.listForCycle(tenantId, cycleId);
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAuthority('PERF_READ')")
    @Operation(summary = "Poll a single launch batch's progress")
    public AppraisalCycleLaunchBatchResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID cycleId,
            @PathVariable UUID batchId) {
        return launches.getBatch(tenantId, cycleId, batchId);
    }
}
