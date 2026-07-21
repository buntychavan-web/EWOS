package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateResponse;
import com.ewos.ats.api.dto.ChangeCandidateStatusRequest;
import com.ewos.ats.api.dto.CreateCandidateRequest;
import com.ewos.ats.api.dto.CreateCandidateResult;
import com.ewos.ats.api.dto.DuplicateCandidateMatchResponse;
import com.ewos.ats.api.dto.UpdateCandidateRequest;
import com.ewos.ats.application.CandidateService;
import com.ewos.ats.domain.CandidateStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ats/candidates")
@Tag(
        name = "ATS Candidates",
        description = "Candidate master + status lifecycle + duplicate detection")
public class CandidateController {

    private final CandidateService candidates;

    public CandidateController(CandidateService candidates) {
        this.candidates = candidates;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Create a candidate; returns any potential-duplicate hits")
    public ResponseEntity<CreateCandidateResult> create(
            @Valid @RequestBody CreateCandidateRequest req) {
        CreateCandidateResult result = candidates.create(req);
        return ResponseEntity.created(
                        URI.create("/api/v1/ats/candidates/" + result.candidate().id()))
                .body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Update a candidate")
    public CandidateResponse update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCandidateRequest req) {
        return candidates.update(tenantId, id, req);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Change a candidate's lifecycle status")
    public CandidateResponse changeStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeCandidateStatusRequest req) {
        return candidates.changeStatus(tenantId, id, req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "Fetch a candidate by ID")
    public CandidateResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return candidates.getById(tenantId, id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List candidates for a company (optional status filter)")
    public Page<CandidateResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam(required = false) CandidateStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return candidates.list(
                tenantId,
                companyId,
                status,
                PageRequest.of(page, Math.min(size, 100), Sort.by("lastName").ascending()));
    }

    @GetMapping("/duplicates")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "Check for potential-duplicate candidates by email / phone")
    public List<DuplicateCandidateMatchResponse> checkDuplicates(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        return candidates.checkDuplicates(tenantId, email, phone);
    }
}
