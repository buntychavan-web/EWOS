package com.ewos.ats.api;

import com.ewos.ats.api.dto.AdvanceApplicationRequest;
import com.ewos.ats.api.dto.CreateApplicationRequest;
import com.ewos.ats.api.dto.JobApplicationResponse;
import com.ewos.ats.api.dto.RejectApplicationRequest;
import com.ewos.ats.api.dto.WithdrawApplicationRequest;
import com.ewos.ats.application.JobApplicationService;
import com.ewos.ats.domain.ApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ats/applications")
@Tag(
        name = "ATS Applications",
        description =
                "Job applications tying candidates to requisitions. Subject-type"
                        + " 'ats.application'.")
public class JobApplicationController {

    private final JobApplicationService applications;

    public JobApplicationController(JobApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Create a job application for a candidate against a requisition")
    public ResponseEntity<JobApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest req) {
        JobApplicationResponse created = applications.create(req);
        return ResponseEntity.created(URI.create("/api/v1/ats/applications/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Advance the application forward in its pipeline")
    public JobApplicationResponse advance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AdvanceApplicationRequest req) {
        return applications.advance(tenantId, id, req);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Put an application on hold")
    public JobApplicationResponse hold(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return applications.hold(tenantId, id);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Resume a held application to the given non-hold status")
    public JobApplicationResponse resume(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AdvanceApplicationRequest req) {
        return applications.resume(tenantId, id, req);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Reject a non-terminal application")
    public JobApplicationResponse reject(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody RejectApplicationRequest req) {
        return applications.reject(tenantId, id, req);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Mark the application withdrawn by candidate")
    public JobApplicationResponse withdraw(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawApplicationRequest req) {
        return applications.withdraw(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "Fetch an application by ID")
    public JobApplicationResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return applications.getById(tenantId, id);
    }

    @GetMapping("/by-candidate/{candidateId}")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List applications for a candidate")
    public List<JobApplicationResponse> byCandidate(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return applications.forCandidate(tenantId, candidateId);
    }

    @GetMapping("/by-requisition/{requisitionId}")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List applications for a requisition")
    public List<JobApplicationResponse> byRequisition(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID requisitionId) {
        return applications.forRequisition(tenantId, requisitionId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List applications for a company by status")
    public Page<JobApplicationResponse> byStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return applications.byStatus(
                tenantId,
                companyId,
                status,
                PageRequest.of(page, Math.min(size, 100), Sort.by("appliedAt").descending()));
    }
}
