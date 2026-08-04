package com.ewos.payroll.api;

import com.ewos.payroll.api.dto.PayslipBrandingConfigurationResponse;
import com.ewos.payroll.api.dto.UpsertPayslipBrandingConfigurationRequest;
import com.ewos.payroll.application.PayslipBrandingConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payroll/payslip-branding")
@Tag(name = "Payslip Branding", description = "Employer branding applied to generated payslip PDFs")
public class PayslipBrandingConfigurationController {

    private final PayslipBrandingConfigurationService service;

    public PayslipBrandingConfigurationController(PayslipBrandingConfigurationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYROLL_CONFIG')")
    @Operation(summary = "Create or update a company's payslip PDF branding")
    public PayslipBrandingConfigurationResponse upsert(
            @Valid @RequestBody UpsertPayslipBrandingConfigurationRequest request) {
        return service.upsert(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAYROLL_READ')")
    @Operation(summary = "Fetch a company's payslip PDF branding, if configured")
    public PayslipBrandingConfigurationResponse getForCompany(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return service.getForCompany(tenantId, companyId);
    }
}
