package com.ewos.ats.api;

import com.ewos.ats.api.dto.CandidateDocumentResponse;
import com.ewos.ats.api.dto.UploadCandidateDocumentRequest;
import com.ewos.ats.application.CandidateDocumentService;
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
@Tag(name = "ATS Documents", description = "Supporting documents attached to a candidate")
public class CandidateDocumentController {

    private final CandidateDocumentService documents;

    public CandidateDocumentController(CandidateDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping("/candidates/{candidateId}/documents")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Attach a supporting document to a candidate")
    public CandidateDocumentResponse upload(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID candidateId,
            @Valid @RequestBody UploadCandidateDocumentRequest req) {
        return documents.upload(tenantId, candidateId, req);
    }

    @GetMapping("/candidates/{candidateId}/documents")
    @PreAuthorize("hasAuthority('ATS_READ')")
    @Operation(summary = "List documents for a candidate")
    public List<CandidateDocumentResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID candidateId) {
        return documents.listForCandidate(tenantId, candidateId);
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAuthority('ATS_WRITE')")
    @Operation(summary = "Soft-delete a candidate document")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID documentId) {
        documents.delete(tenantId, documentId);
        return ResponseEntity.noContent().build();
    }
}
