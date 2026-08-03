package com.ewos.payroll.api;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.application.PayslipPdfService;
import com.ewos.payroll.application.PayslipService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 3 — Employee Self-Service over Payroll. Requires only authentication (no PAYROLL_READ);
 * delegates entirely to {@link PayslipService}, mirroring {@code EmployeeController.me()}'s (Sprint
 * 1.3) resolution pattern.
 */
@RestController
@RequestMapping("/api/v1/payroll/self-service")
@Tag(name = "Payslip Self-Service", description = "An employee's own payslips")
public class PayslipSelfServiceController {

    private final PayslipService service;
    private final PayslipPdfService pdfService;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public PayslipSelfServiceController(
            PayslipService service,
            PayslipPdfService pdfService,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.service = service;
        this.pdfService = pdfService;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    @GetMapping("/payslips")
    @Operation(summary = "The caller's own payslips")
    public List<PayslipResponse> myPayslips() {
        var employeeId =
                employeeContext
                        .currentEmployeeId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));
        return service.forEmployee(tenantContext.homeTenantId(), employeeId);
    }

    @GetMapping("/payslips/{id}")
    @Operation(summary = "Detail of one of the caller's own payslips, line by line")
    public PayslipResponse myPayslip(@PathVariable UUID id) {
        var employeeId =
                employeeContext
                        .currentEmployeeId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));
        return service.getOwnPayslip(tenantContext.homeTenantId(), employeeId, id);
    }

    @GetMapping("/payslips/{id}/pdf")
    @Operation(summary = "Download one of the caller's own payslips as a PDF")
    public ResponseEntity<byte[]> downloadOwnPayslipPdf(@PathVariable UUID id) {
        var employeeId =
                employeeContext
                        .currentEmployeeId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));
        byte[] pdf = pdfService.generateForSelf(tenantContext.homeTenantId(), employeeId, id);
        return PayslipController.pdfResponse(pdf, "payslip.pdf");
    }
}
