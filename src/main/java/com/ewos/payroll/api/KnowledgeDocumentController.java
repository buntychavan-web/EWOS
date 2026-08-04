package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.CreateKnowledgeDocumentRequest;
import com.ewos.payroll.api.dto.KnowledgeDocumentResponse;
import com.ewos.payroll.application.KnowledgeDocumentService;
import com.ewos.payroll.domain.KnowledgeSourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24K item 7 — Knowledge Centre backend foundation. Statutory source documents and company
 * payroll policies with version history, effective dates, and plain-text search; not the Knowledge
 * Centre feature itself (no AI retrieval here — see {@code KnowledgeDocumentService}).
 */
@RestController
@RequestMapping("/api/v1/payroll/knowledge-documents")
@Tag(name = "Knowledge Centre", description = "Versioned statutory sources and payroll policies")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService service;

    public KnowledgeDocumentController(KnowledgeDocumentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create a new document family at version 1 (status DRAFT)")
    public KnowledgeDocumentResponse create(
            @Valid @RequestBody CreateKnowledgeDocumentRequest request) {
        return service.create(request);
    }

    @PostMapping("/{documentFamilyId}/versions")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Add a new DRAFT version to an existing document family")
    public KnowledgeDocumentResponse createNewVersion(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID documentFamilyId,
            @Valid @RequestBody CreateKnowledgeDocumentRequest request) {
        return service.createNewVersion(tenantId, documentFamilyId, request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Publish this version, superseding whichever was previously PUBLISHED")
    public KnowledgeDocumentResponse publish(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.publish(tenantId, id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Archive this version")
    public KnowledgeDocumentResponse archive(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.archive(tenantId, id);
    }

    @GetMapping("/family/{documentFamilyId}/history")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Full version history for a document family, newest first")
    public List<KnowledgeDocumentResponse> versionHistory(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID documentFamilyId) {
        return service.versionHistory(tenantId, documentFamilyId);
    }

    @GetMapping("/effective")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Every PUBLISHED document effective on a given date")
    public List<KnowledgeDocumentResponse> effectiveAsOf(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) KnowledgeSourceType sourceType,
            @RequestParam(required = false) LocalDate asOf) {
        return service.effectiveAsOf(
                tenantId, companyId, sourceType, asOf != null ? asOf : LocalDate.now());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Plain-text search over PUBLISHED documents' title/summary/tags")
    public List<KnowledgeDocumentResponse> search(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam String query) {
        return service.search(tenantId, query);
    }
}
