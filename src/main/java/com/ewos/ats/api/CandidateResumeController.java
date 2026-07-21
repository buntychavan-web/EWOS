package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateResumeResponse;
import com.ewos.ats.api.dto.UploadResumeRequest;
import com.ewos.ats.application.CandidateResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ats")
@Tag(name = "ATS Resumes", description = "Candidate resume catalogue + parsing framework")
public class CandidateResumeController {

    private final CandidateResumeService resumes;

    public CandidateResumeController(CandidateResumeService resumes) {
        this.resumes = resumes;
    }

    @PostMapping("/candidates/{candidateId}/resumes")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Register a resume file for a candidate (metadata + storage URI)")
    public CandidateResumeResponse upload(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody UploadResumeRequest req) {
        return resumes.upload(tenantId, candidateId, req);
    }

    @GetMapping("/candidates/{candidateId}/resumes")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List resumes for a candidate")
    public List<CandidateResumeResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return resumes.listForCandidate(tenantId, candidateId);
    }

    @PostMapping("/resumes/{resumeId}/primary")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Mark this resume as the candidate's primary")
    public CandidateResumeResponse markPrimary(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resumeId) {
        return resumes.markPrimary(tenantId, resumeId);
    }

    @DeleteMapping("/resumes/{resumeId}")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Soft-delete a resume")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resumeId) {
        resumes.delete(tenantId, resumeId);
        return ResponseEntity.noContent().build();
    }
}
