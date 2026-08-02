package com.ewos.payroll.api;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.payroll.api.dto.EmployeeTaxDeclarationResponse;
import com.ewos.payroll.api.dto.PayrollDashboardResponse;
import com.ewos.payroll.api.dto.SelfServiceTaxDeclarationRequest;
import com.ewos.payroll.api.dto.TaxDeclarationProofResponse;
import com.ewos.payroll.api.dto.TaxProjectionResponse;
import com.ewos.payroll.api.dto.UploadTaxDeclarationProofRequest;
import com.ewos.payroll.application.EmployeeTaxDeclarationService;
import com.ewos.payroll.application.PayrollSelfServiceService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24J — the rest of the Employee Self-Service payroll experience beyond payslips ({@link
 * PayslipSelfServiceController}): a dashboard aggregating data that already exists elsewhere,
 * self-service investment declaration + proof upload, and an on-demand tax projection. Requires
 * only authentication (no PAYROLL_* authority) — every method resolves the caller's own employeeId
 * server-side and never trusts one from the request body, mirroring {@code
 * PayslipSelfServiceController}'s pattern exactly.
 */
@RestController
@RequestMapping("/api/v1/payroll/self-service")
@Tag(name = "Payroll Self-Service", description = "Dashboard, tax declaration, and tax projection")
public class PayrollSelfServiceController {

    private final PayrollSelfServiceService service;
    private final EmployeeTaxDeclarationService taxDeclarations;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public PayrollSelfServiceController(
            PayrollSelfServiceService service,
            EmployeeTaxDeclarationService taxDeclarations,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.service = service;
        this.taxDeclarations = taxDeclarations;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Current salary summary, latest payslip, and YTD tax declaration")
    public PayrollDashboardResponse dashboard() {
        return service.dashboard(tenantContext.homeTenantId(), requireEmployeeId());
    }

    @GetMapping("/tax-projection")
    @Operation(summary = "Projected annual tax liability using the current fiscal year's data")
    public TaxProjectionResponse taxProjection() {
        return service.taxProjection(tenantContext.homeTenantId(), requireEmployeeId());
    }

    @GetMapping("/tax-declaration")
    @Operation(summary = "The caller's own investment declaration for a fiscal year")
    public EmployeeTaxDeclarationResponse ownDeclaration(@RequestParam String fiscalYear) {
        return service.ownDeclaration(
                tenantContext.homeTenantId(), requireEmployeeId(), fiscalYear);
    }

    @PutMapping("/tax-declaration")
    @Operation(summary = "Create or update the caller's own investment declaration")
    public EmployeeTaxDeclarationResponse upsertOwnDeclaration(
            @Valid @RequestBody SelfServiceTaxDeclarationRequest request) {
        return service.upsertOwnDeclaration(
                tenantContext.homeTenantId(), requireEmployeeId(), request);
    }

    @GetMapping("/tax-declaration/{declarationId}/proofs")
    @Operation(summary = "Investment proofs uploaded against one of the caller's own declarations")
    public List<TaxDeclarationProofResponse> proofs(@PathVariable UUID declarationId) {
        requireOwnDeclaration(declarationId);
        return taxDeclarations.proofsForDeclaration(tenantContext.homeTenantId(), declarationId);
    }

    @PostMapping("/tax-declaration/{declarationId}/proofs")
    @Operation(summary = "Record an uploaded investment-proof document (metadata + storage URI)")
    public TaxDeclarationProofResponse uploadProof(
            @PathVariable UUID declarationId,
            @Valid @RequestBody UploadTaxDeclarationProofRequest request) {
        requireOwnDeclaration(declarationId);
        return taxDeclarations.uploadProof(tenantContext.homeTenantId(), declarationId, request);
    }

    /** Confirms the declaration belongs to the caller before any proof read/write against it. */
    private void requireOwnDeclaration(UUID declarationId) {
        UUID employeeId = requireEmployeeId();
        EmployeeTaxDeclarationResponse declaration =
                taxDeclarations.getById(tenantContext.homeTenantId(), declarationId);
        if (!employeeId.equals(declaration.employeeId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tax declaration not found");
        }
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
}
