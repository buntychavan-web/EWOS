package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.application.PayslipPdfService;
import com.ewos.payroll.application.PayslipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/payslips")
@Tag(name = "Payslips", description = "Immutable per-employee snapshots of a payroll run")
public class PayslipController {

    private final PayslipService service;
    private final PayslipPdfService pdfService;

    public PayslipController(PayslipService service, PayslipPdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a single payslip by ID")
    public PayslipResponse getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return service.getById(tenantId, id);
    }

    @GetMapping("/run/{runId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All payslips for one payroll run")
    public List<PayslipResponse> forRun(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        return service.forRun(tenantId, runId);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "All historical payslips for an employee")
    public List<PayslipResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return service.forEmployee(tenantId, employeeId);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Download one payslip as a PDF")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        byte[] pdf = pdfService.generateForAdmin(tenantId, id);
        return pdfResponse(pdf, "payslip.pdf");
    }

    @GetMapping("/run/{runId}/pdf")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Bulk download every payslip on a run as a ZIP of individual PDFs")
    public ResponseEntity<byte[]> downloadRunPdfZip(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID runId) {
        byte[] zip = pdfService.generateZipForRun(tenantId, runId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("payslips.zip").build().toString())
                .body(zip);
    }

    static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
