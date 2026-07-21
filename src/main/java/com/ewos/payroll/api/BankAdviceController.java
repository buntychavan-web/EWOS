package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.BankAdviceResponse;
import com.ewos.payroll.api.dto.GenerateBankAdviceRequest;
import com.ewos.payroll.api.dto.MarkPaymentFailedRequest;
import com.ewos.payroll.api.dto.MarkPaymentPaidRequest;
import com.ewos.payroll.application.BankAdviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/bank-advices")
@Tag(name = "Bank Advices", description = "Salary-remittance batches for finalized payroll runs")
public class BankAdviceController {

    private final BankAdviceService service;

    public BankAdviceController(BankAdviceService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Generate an advice + payment instructions for a finalized run")
    public ResponseEntity<BankAdviceResponse> generate(
            @Valid @RequestBody GenerateBankAdviceRequest request) {
        BankAdviceResponse created = service.generate(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/bank-advices/" + created.id()))
                .body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch by ID with all payment instructions")
    public BankAdviceResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/run/{runId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All advices generated for a payroll run")
    public List<BankAdviceResponse> forRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        return service.forRun(tenantId, runId);
    }

    @GetMapping(value = "/{id}/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Download the advice file (CSV)")
    public ResponseEntity<String> export(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        String body = service.export(tenantId, id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bank-advice-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Mark the advice as received by the bank")
    public BankAdviceResponse acknowledge(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.acknowledge(tenantId, id);
    }

    @PostMapping("/{id}/fail")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Mark the whole advice as FAILED with a reason")
    public BankAdviceResponse fail(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody MarkPaymentFailedRequest request) {
        return service.markFailed(tenantId, id, request.failureReason());
    }

    @PostMapping("/{id}/instructions/{instructionId}/paid")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Record a successful settlement for an individual instruction")
    public BankAdviceResponse markInstructionPaid(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @PathVariable UUID instructionId,
            @Valid @RequestBody MarkPaymentPaidRequest request) {
        return service.markInstructionPaid(
                tenantId, id, instructionId, request.settlementReference());
    }

    @PostMapping("/{id}/instructions/{instructionId}/failed")
    @PreAuthorize("hasAuthority('PAYROLL_ADMIN')")
    @Operation(summary = "Record a rejection for an individual instruction")
    public BankAdviceResponse markInstructionFailed(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @PathVariable UUID instructionId,
            @Valid @RequestBody MarkPaymentFailedRequest request) {
        return service.markInstructionFailed(tenantId, id, instructionId, request.failureReason());
    }
}
