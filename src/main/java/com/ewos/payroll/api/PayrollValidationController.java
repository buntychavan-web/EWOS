package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.PayrollValidationReportResponse;
import com.ewos.payroll.application.PayrollValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/validate")
@Tag(name = "Payroll Validation", description = "Pre-flight checks before starting a payroll run")
public class PayrollValidationController {

    private final PayrollValidationService service;

    public PayrollValidationController(PayrollValidationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_RUN')")
    @Operation(summary = "Run pre-flight validation for a company + period")
    public PayrollValidationReportResponse validate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID payrollPeriodId) {
        return service.validate(tenantId, companyId, payrollPeriodId);
    }
}
