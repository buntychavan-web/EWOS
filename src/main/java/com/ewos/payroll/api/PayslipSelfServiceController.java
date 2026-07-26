package com.ewos.payroll.api;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.payroll.api.dto.PayslipResponse;
import com.ewos.payroll.application.PayslipService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public PayslipSelfServiceController(
            PayslipService service, TenantContext tenantContext, EmployeeContext employeeContext) {
        this.service = service;
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
}
