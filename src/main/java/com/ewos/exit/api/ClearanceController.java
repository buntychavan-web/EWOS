package com.ewos.exit.api;

import com.ewos.exit.api.dto.ClearanceResponse;
import com.ewos.exit.api.dto.CreateClearanceRequest;
import com.ewos.exit.api.dto.CreateKtItemRequest;
import com.ewos.exit.api.dto.DocumentResponse;
import com.ewos.exit.api.dto.InterviewResponse;
import com.ewos.exit.api.dto.IssueDocumentRequest;
import com.ewos.exit.api.dto.KtItemResponse;
import com.ewos.exit.api.dto.RecordInterviewRequest;
import com.ewos.exit.api.dto.UpdateClearanceRequest;
import com.ewos.exit.application.ExitService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exit")
@Tag(name = "Exit — Clearance", description = "Clearance, KT, interview, exit documents")
public class ClearanceController {

    private final ExitService exit;

    public ClearanceController(ExitService exit) {
        this.exit = exit;
    }

    // Clearance --------------------------------------------------------------

    @PostMapping("/resignations/{resignationId}/clearances")
    @PreAuthorize("hasAuthority('EXIT_WRITE')")
    @Operation(summary = "Create a clearance item for a department")
    public ResponseEntity<ClearanceResponse> addClearance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID resignationId,
            @Valid @RequestBody CreateClearanceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exit.addClearance(tenantId, resignationId, req));
    }

    @PutMapping("/clearances/{clearanceId}")
    @PreAuthorize("hasAuthority('EXIT_CLEAR')")
    @Operation(summary = "Update clearance status (clear / block / progress)")
    public ClearanceResponse updateClearance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID clearanceId,
            @Valid @RequestBody UpdateClearanceRequest req) {
        return exit.updateClearance(tenantId, clearanceId, req);
    }

    @GetMapping("/resignations/{resignationId}/clearances")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "List clearance items for a resignation")
    public List<ClearanceResponse> listClearances(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resignationId) {
        return exit.listClearances(tenantId, resignationId);
    }

    // KT ---------------------------------------------------------------------

    @PostMapping("/resignations/{resignationId}/kt")
    @PreAuthorize("hasAuthority('EXIT_WRITE')")
    @Operation(summary = "Add a knowledge-transfer item")
    public ResponseEntity<KtItemResponse> addKt(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID resignationId,
            @Valid @RequestBody CreateKtItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exit.addKtItem(tenantId, resignationId, req));
    }

    @PostMapping("/kt/{ktItemId}/complete")
    @PreAuthorize("hasAuthority('EXIT_WRITE')")
    @Operation(summary = "Mark a KT item complete")
    public KtItemResponse completeKt(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID ktItemId) {
        return exit.completeKtItem(tenantId, ktItemId);
    }

    @GetMapping("/resignations/{resignationId}/kt")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "List KT items for a resignation")
    public List<KtItemResponse> listKt(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resignationId) {
        return exit.listKtItems(tenantId, resignationId);
    }

    // Interview --------------------------------------------------------------

    @PostMapping("/resignations/{resignationId}/interview")
    @PreAuthorize("hasAuthority('EXIT_INTERVIEW')")
    @Operation(summary = "Record an exit interview (create or upsert)")
    public ResponseEntity<InterviewResponse> recordInterview(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID resignationId,
            @Valid @RequestBody RecordInterviewRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exit.recordInterview(tenantId, resignationId, req));
    }

    @GetMapping("/resignations/{resignationId}/interview")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "Fetch the exit interview")
    public InterviewResponse getInterview(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resignationId) {
        return exit.getInterview(tenantId, resignationId);
    }

    // Documents --------------------------------------------------------------

    @PostMapping("/resignations/{resignationId}/documents")
    @PreAuthorize("hasAuthority('EXIT_ISSUE_DOC')")
    @Operation(summary = "Issue an exit document (relieving letter, experience letter, etc.)")
    public ResponseEntity<DocumentResponse> issueDocument(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID resignationId,
            @Valid @RequestBody IssueDocumentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(exit.issueDocument(tenantId, resignationId, req));
    }

    @GetMapping("/resignations/{resignationId}/documents")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "List issued exit documents")
    public List<DocumentResponse> listDocuments(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID resignationId) {
        return exit.listDocuments(tenantId, resignationId);
    }
}
