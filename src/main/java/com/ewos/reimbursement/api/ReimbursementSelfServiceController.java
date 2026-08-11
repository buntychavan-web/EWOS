package com.ewos.reimbursement.api;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.reimbursement.api.dto.CreateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.ReceiptExtractionRequest;
import com.ewos.reimbursement.api.dto.ReceiptExtractionResponse;
import com.ewos.reimbursement.api.dto.ReimbursementCategoryResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimAttachmentResponse;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.api.dto.UpdateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.UploadReimbursementAttachmentRequest;
import com.ewos.reimbursement.application.ReimbursementClaimService;
import com.ewos.reimbursement.application.ReimbursementReceiptExtractionService;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 2 — the caller's own reimbursement claims. {@code employeeId} is always resolved from
 * {@link EmployeeContext#currentEmployeeId()}, mirroring {@link
 * com.ewos.employee.api.EssProfileController} exactly — never accepted from the request.
 */
@RestController
@RequestMapping("/api/v1/self-service/reimbursements")
@Tag(name = "Reimbursement Self-Service", description = "The caller's own reimbursement claims")
public class ReimbursementSelfServiceController {

    private static final String HEADER = "Idempotency-Key";

    private final ReimbursementClaimService claims;
    private final ReimbursementReceiptExtractionService extraction;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;
    private final IdempotencyService idempotency;

    public ReimbursementSelfServiceController(
            ReimbursementClaimService claims,
            ReimbursementReceiptExtractionService extraction,
            EmployeeContext employeeContext,
            TenantContext tenantContext,
            IdempotencyService idempotency) {
        this.claims = claims;
        this.extraction = extraction;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
        this.idempotency = idempotency;
    }

    @GetMapping("/categories")
    @Operation(summary = "Active reimbursement categories the caller may claim against")
    public List<ReimbursementCategoryResponse> categories() {
        return claims.listCategories(tenantContext.homeTenantId());
    }

    @PostMapping
    @Operation(
            summary = "Create a DRAFT reimbursement claim against the caller's own employee record")
    public ResponseEntity<ReimbursementClaimResponse> create(
            @Valid @RequestBody CreateReimbursementClaimRequest request) {
        ReimbursementClaimResponse created =
                claims.createDraft(tenantContext.homeTenantId(), requireEmployeeId(), request);
        return ResponseEntity.created(
                        URI.create("/api/v1/self-service/reimbursements/" + created.id()))
                .body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update the caller's own DRAFT claim; every field optional")
    public ReimbursementClaimResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateReimbursementClaimRequest request) {
        return claims.updateDraft(tenantContext.homeTenantId(), requireEmployeeId(), id, request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit the caller's own DRAFT claim for manager approval")
    public ReimbursementClaimResponse submit(
            @PathVariable UUID id,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        return idempotency.execute(
                tenantId,
                requireActorId(),
                "reimbursement.claim.submit",
                idempotencyKey,
                ReimbursementClaimResponse.class,
                () -> claims.submit(tenantId, employeeId, id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel the caller's own DRAFT or SUBMITTED claim")
    public ReimbursementClaimResponse cancel(
            @PathVariable UUID id,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        return idempotency.execute(
                tenantId,
                requireActorId(),
                "reimbursement.claim.cancel",
                idempotencyKey,
                ReimbursementClaimResponse.class,
                () -> claims.cancel(tenantId, employeeId, id));
    }

    @GetMapping
    @Operation(summary = "The caller's own reimbursement claims, most-recent-first")
    public Page<ReimbursementClaimResponse> list(
            @RequestParam(required = false) ReimbursementClaimStatus status,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return claims.listMine(tenantContext.homeTenantId(), requireEmployeeId(), status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "View one of the caller's own claims")
    public ReimbursementClaimResponse get(@PathVariable UUID id) {
        return claims.getMine(tenantContext.homeTenantId(), requireEmployeeId(), id);
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "Attach receipt/document metadata to one of the caller's own claims")
    public ReimbursementClaimAttachmentResponse addAttachment(
            @PathVariable UUID id,
            @Valid @RequestBody UploadReimbursementAttachmentRequest request) {
        return claims.addAttachment(tenantContext.homeTenantId(), requireEmployeeId(), id, request);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Remove an attachment from one of the caller's own DRAFT claims")
    public void deleteAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        claims.deleteAttachment(
                tenantContext.homeTenantId(), requireEmployeeId(), id, attachmentId);
    }

    @PostMapping("/receipts/extract")
    @Operation(
            summary =
                    "Best-effort OCR suggestion for a scanned/uploaded receipt — never authoritative;"
                            + " the employee's own confirmed create/update request is the only path that"
                            + " writes a claim's real values")
    public ReceiptExtractionResponse extract(@Valid @RequestBody ReceiptExtractionRequest request) {
        return extraction.extract(request);
    }

    private UUID requireEmployeeId() {
        return employeeContext
                .currentEmployeeId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }

    private UUID requireActorId() {
        return tenantContext
                .currentUserId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.UNAUTHORIZED, "Authenticated user required"));
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, HEADER + " header is required");
        }
    }
}
