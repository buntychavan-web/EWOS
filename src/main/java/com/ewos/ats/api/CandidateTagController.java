package com.ewos.ats.api;

import com.ewos.ats.api.dto.AddCandidateTagRequest;
import com.ewos.ats.api.dto.CandidateTagResponse;
import com.ewos.ats.application.CandidateTagService;
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
@RequestMapping("/api/v1/ats/candidates/{candidateId}/tags")
@Tag(name = "ATS Tags", description = "Candidate tags (skill / attribute labels)")
public class CandidateTagController {

    private final CandidateTagService tags;

    public CandidateTagController(CandidateTagService tags) {
        this.tags = tags;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Add a tag to a candidate")
    public CandidateTagResponse add(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody AddCandidateTagRequest req) {
        return tags.addTag(tenantId, candidateId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List a candidate's tags")
    public List<CandidateTagResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return tags.listForCandidate(tenantId, candidateId);
    }

    @DeleteMapping("/{tag}")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Remove a tag from a candidate")
    public ResponseEntity<Void> remove(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @PathVariable String tag) {
        tags.removeTag(tenantId, candidateId, tag);
        return ResponseEntity.noContent().build();
    }
}
