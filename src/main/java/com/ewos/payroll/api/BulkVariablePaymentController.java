package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.BulkVariablePaymentReportResponse;
import com.ewos.payroll.api.dto.BulkVariablePaymentUploadRequest;
import com.ewos.payroll.application.BulkVariablePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/bulk-variable-payments")
@Tag(
        name = "Bulk Variable Payments",
        description = "Bulk upload of Bonus/Incentives/Variable Pay/Arrears/Adjustments")
public class BulkVariablePaymentController {

    private final BulkVariablePaymentService service;

    public BulkVariablePaymentController(BulkVariablePaymentService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(summary = "Validate every row without creating anything")
    public BulkVariablePaymentReportResponse preview(
            @Valid @RequestBody BulkVariablePaymentUploadRequest request) {
        return service.preview(request);
    }

    @PostMapping("/commit")
    @PreAuthorize("hasAuthority('PAYROLL_WRITE')")
    @Operation(
            summary =
                    "Commit the batch: creates one arrear per row if every row is valid, otherwise"
                            + " creates nothing and returns the same per-row error report")
    public BulkVariablePaymentReportResponse commit(
            @Valid @RequestBody BulkVariablePaymentUploadRequest request) {
        return service.commit(request);
    }

    @GetMapping("/{batchId}")
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a previously committed/rejected batch's audit summary")
    public BulkVariablePaymentReportResponse getBatch(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID batchId) {
        return service.getBatch(tenantId, batchId);
    }
}
