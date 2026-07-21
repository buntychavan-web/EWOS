package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.GeneratePayrollJournalRequest;
import com.ewos.payroll.api.dto.JournalReconciliationResponse;
import com.ewos.payroll.api.dto.PayrollJournalResponse;
import com.ewos.payroll.application.PayrollJournalService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/journals")
@Tag(name = "Payroll Journals", description = "Double-entry journals for finalized payroll runs")
public class PayrollJournalController {

    private final PayrollJournalService service;

    public PayrollJournalController(PayrollJournalService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Generate a journal for a finalized run using the tenant's GL mappings")
    public ResponseEntity<PayrollJournalResponse> generate(
            @Valid @RequestBody GeneratePayrollJournalRequest request) {
        PayrollJournalResponse created = service.generate(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/journals/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Approve a DRAFT journal; enforces balanced debits vs credits")
    public PayrollJournalResponse approve(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.approve(tenantId, id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Post an APPROVED journal to the GL")
    public PayrollJournalResponse post(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.post(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Cancel a non-posted journal")
    public PayrollJournalResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.cancel(tenantId, id);
    }

    @PostMapping("/{id}/record-export")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Record that a POSTED journal has been exported to an ERP")
    public PayrollJournalResponse recordExport(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestParam String format,
            @RequestParam(required = false) String reference) {
        return service.recordExport(tenantId, id, format, reference);
    }

    @GetMapping(value = "/{id}/export", produces = "text/csv")
    @PreAuthorize("hasAuthority('PAYROLL_ACCOUNTING')")
    @Operation(summary = "Download the journal as generic CSV")
    public ResponseEntity<String> exportCsv(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        String body = service.exportCsv(tenantId, id);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"journal-" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping("/{id}/reconcile")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Reconcile a journal's totals against the parent run's totals")
    public JournalReconciliationResponse reconcile(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.reconcile(tenantId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a journal by ID")
    public PayrollJournalResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/run/{runId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All journals for a payroll run")
    public List<PayrollJournalResponse> forRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        return service.forRun(tenantId, runId);
    }
}
