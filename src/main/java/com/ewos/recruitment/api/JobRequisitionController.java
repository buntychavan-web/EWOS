package com.ewos.recruitment.api;

import com.ewos.recruitment.api.dto.CloseJobRequisitionRequest;
import com.ewos.recruitment.api.dto.CreateJobRequisitionRequest;
import com.ewos.recruitment.api.dto.DecideJobRequisitionRequest;
import com.ewos.recruitment.api.dto.JobRequisitionResponse;
import com.ewos.recruitment.api.dto.RecordFillRequest;
import com.ewos.recruitment.api.dto.SubmitJobRequisitionRequest;
import com.ewos.recruitment.api.dto.UpdateJobRequisitionRequest;
import com.ewos.recruitment.application.JobRequisitionService;
import com.ewos.recruitment.domain.RequisitionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recruitment/requisitions")
@Tag(
        name = "Recruitment Requisitions",
        description =
                "Job requisitions with approval workflow. Subject-type"
                        + " 'recruitment.requisition'.")
public class JobRequisitionController {

    private final JobRequisitionService requisitions;

    public JobRequisitionController(JobRequisitionService requisitions) {
        this.requisitions = requisitions;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Create a DRAFT job requisition")
    public ResponseEntity<JobRequisitionResponse> create(
            @Valid @RequestBody CreateJobRequisitionRequest req) {
        JobRequisitionResponse created = requisitions.create(req);
        return ResponseEntity.created(
                        URI.create("/api/v1/recruitment/requisitions/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Update a DRAFT requisition")
    public JobRequisitionResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobRequisitionRequest req) {
        return requisitions.update(tenantId, id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Submit for approval — starts a workflow instance")
    public JobRequisitionResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody SubmitJobRequisitionRequest req) {
        return requisitions.submit(tenantId, id, req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('RECRUITMENT_APPROVE')")
    @Operation(summary = "Approve a pending requisition")
    public JobRequisitionResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) DecideJobRequisitionRequest req) {
        return requisitions.approve(tenantId, id, req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('RECRUITMENT_APPROVE')")
    @Operation(summary = "Reject a pending requisition")
    public JobRequisitionResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) DecideJobRequisitionRequest req) {
        return requisitions.reject(tenantId, id, req);
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Open an APPROVED requisition for hiring")
    public JobRequisitionResponse open(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return requisitions.open(tenantId, id);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Put an OPEN requisition on hold")
    public JobRequisitionResponse hold(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return requisitions.hold(tenantId, id);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Resume a requisition from ON_HOLD")
    public JobRequisitionResponse resume(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return requisitions.resume(tenantId, id);
    }

    @PostMapping("/{id}/fill")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Record one or more fills against the requisition")
    public JobRequisitionResponse fill(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RecordFillRequest req) {
        return requisitions.recordFill(tenantId, id, req);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Close a requisition")
    public JobRequisitionResponse close(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseJobRequisitionRequest req) {
        return requisitions.close(tenantId, id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RECRUITMENT_WRITE')")
    @Operation(summary = "Cancel a non-terminal requisition")
    public JobRequisitionResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CloseJobRequisitionRequest req) {
        return requisitions.cancel(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_READ')")
    @Operation(summary = "Fetch a requisition by ID")
    public JobRequisitionResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return requisitions.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_READ')")
    @Operation(summary = "List requisitions for a company by status")
    public List<JobRequisitionResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam RequisitionStatus status) {
        return requisitions.byStatus(tenantId, companyId, status);
    }
}
