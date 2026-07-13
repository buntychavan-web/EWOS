package com.ewos.company.api;

import com.ewos.common.exception.ApiError;
import com.ewos.company.api.dto.AddBankAccountRequest;
import com.ewos.company.api.dto.AddStatutoryRegistrationRequest;
import com.ewos.company.api.dto.AssignPolicyRequest;
import com.ewos.company.api.dto.AssignSharedServiceRequest;
import com.ewos.company.api.dto.BankAccountResponse;
import com.ewos.company.api.dto.CompanyResponse;
import com.ewos.company.api.dto.CompanyStatusRequest;
import com.ewos.company.api.dto.CompanyVersionResponse;
import com.ewos.company.api.dto.CreateCompanyRequest;
import com.ewos.company.api.dto.PolicyAssignmentResponse;
import com.ewos.company.api.dto.RetireRequest;
import com.ewos.company.api.dto.SharedServiceResponse;
import com.ewos.company.api.dto.StatutoryRegistrationResponse;
import com.ewos.company.api.dto.UpdateCompanyProfileRequest;
import com.ewos.company.application.BankAccountService;
import com.ewos.company.application.CompanyService;
import com.ewos.company.application.PolicyAssignmentService;
import com.ewos.company.application.SharedServiceAssignmentService;
import com.ewos.company.application.StatutoryRegistrationService;
import com.ewos.company.domain.PolicyType;
import com.ewos.company.domain.StatutoryRegistrationKind;
import com.ewos.company.domain.TeamType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@Tag(
        name = "Company Configuration",
        description =
                "Companies, profile versions, statutory registrations, bank accounts, policy assignments, shared services")
public class CompanyController {

    private final CompanyService companyService;
    private final StatutoryRegistrationService statutoryService;
    private final BankAccountService bankAccountService;
    private final PolicyAssignmentService policyService;
    private final SharedServiceAssignmentService sharedService;

    public CompanyController(
            CompanyService companyService,
            StatutoryRegistrationService statutoryService,
            BankAccountService bankAccountService,
            PolicyAssignmentService policyService,
            SharedServiceAssignmentService sharedService) {
        this.companyService = companyService;
        this.statutoryService = statutoryService;
        this.bankAccountService = bankAccountService;
        this.policyService = policyService;
        this.sharedService = sharedService;
    }

    // -------- Companies --------

    @PostMapping
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Create a company (blank or by cloning an existing one)")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Company created",
                content = @Content(schema = @Schema(implementation = CompanyResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid payload",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Company code already exists in tenant",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CompanyResponse> create(
            @Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse created = companyService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/companies/" + created.id()))
                .body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "Search companies (paged, sorted, filtered)")
    public Page<CompanyResponse> search(
            @Parameter(description = "Filter by tenant id") @RequestParam(required = false)
                    UUID tenantId,
            @Parameter(description = "Exact code match (case-insensitive)")
                    @RequestParam(required = false)
                    String code,
            @Parameter(description = "Filter by active flag") @RequestParam(required = false)
                    Boolean active,
            @Parameter(description = "Substring search across company code")
                    @RequestParam(required = false)
                    String search,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return companyService.search(tenantId, code, active, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "Get a company with its current or as-of profile version")
    public CompanyResponse getById(
            @PathVariable UUID id,
            @Parameter(description = "Return the version effective at this date (ISO-8601)")
                    @RequestParam(required = false)
                    LocalDate asOf) {
        return companyService.getById(id, asOf);
    }

    @PutMapping("/{id}/profile")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(
            summary = "Update the company profile — creates a new effective-dated version",
            description =
                    "Closes the current version the day before the new profile's effectiveFrom and"
                            + " opens a new version. Never overwrites historical rows.")
    public CompanyResponse updateProfile(
            @PathVariable UUID id, @Valid @RequestBody UpdateCompanyProfileRequest request) {
        return companyService.updateProfile(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Activate or deactivate a company")
    public CompanyResponse setStatus(
            @PathVariable UUID id, @Valid @RequestBody CompanyStatusRequest request) {
        return companyService.setStatus(id, request.active());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('COMPANY_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a company (sets deleted_at)")
    public void softDelete(@PathVariable UUID id) {
        companyService.softDelete(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "List every profile version for a company, newest first")
    public List<CompanyVersionResponse> listVersions(@PathVariable UUID id) {
        return companyService.listVersions(id);
    }

    // -------- Statutory registrations --------

    @PostMapping("/{id}/statutory-registrations")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Add a statutory registration (PAN / TAN / GST / PF / ESIC / PT / LWF)")
    public ResponseEntity<StatutoryRegistrationResponse> addStatutory(
            @PathVariable UUID id, @Valid @RequestBody AddStatutoryRegistrationRequest request) {
        StatutoryRegistrationResponse created = statutoryService.add(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/statutory-registrations")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "List statutory registrations for a company; optionally filter by kind")
    public List<StatutoryRegistrationResponse> listStatutory(
            @PathVariable UUID id, @RequestParam(required = false) StatutoryRegistrationKind kind) {
        return statutoryService.list(id, kind);
    }

    @PatchMapping("/{id}/statutory-registrations/{regId}/retire")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Retire a statutory registration by setting its effective_to")
    public StatutoryRegistrationResponse retireStatutory(
            @PathVariable UUID id,
            @PathVariable UUID regId,
            @Valid @RequestBody RetireRequest request) {
        return statutoryService.retire(id, regId, request.effectiveTo());
    }

    // -------- Bank accounts --------

    @PostMapping("/{id}/bank-accounts")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Add a company bank account by purpose")
    public ResponseEntity<BankAccountResponse> addBankAccount(
            @PathVariable UUID id, @Valid @RequestBody AddBankAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bankAccountService.add(id, request));
    }

    @GetMapping("/{id}/bank-accounts")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "List company bank accounts")
    public List<BankAccountResponse> listBankAccounts(@PathVariable UUID id) {
        return bankAccountService.list(id);
    }

    @PatchMapping("/{id}/bank-accounts/{acctId}/status")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Activate or deactivate a bank account")
    public BankAccountResponse setBankAccountStatus(
            @PathVariable UUID id,
            @PathVariable UUID acctId,
            @Valid @RequestBody CompanyStatusRequest request) {
        return bankAccountService.setStatus(id, acctId, request.active());
    }

    // -------- Policy assignments --------

    @PostMapping("/{id}/policy-assignments")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Assign a reusable policy to a company for an effective window")
    public ResponseEntity<PolicyAssignmentResponse> assignPolicy(
            @PathVariable UUID id, @Valid @RequestBody AssignPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(policyService.assign(id, request));
    }

    @GetMapping("/{id}/policy-assignments")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "List policy assignments; optionally filter by policy type")
    public List<PolicyAssignmentResponse> listPolicies(
            @PathVariable UUID id, @RequestParam(required = false) PolicyType policyType) {
        return policyService.list(id, policyType);
    }

    @PatchMapping("/{id}/policy-assignments/{paId}/retire")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Retire a policy assignment by setting its effective_to")
    public PolicyAssignmentResponse retirePolicy(
            @PathVariable UUID id,
            @PathVariable UUID paId,
            @Valid @RequestBody RetireRequest request) {
        return policyService.retire(id, paId, request.effectiveTo());
    }

    // -------- Shared services --------

    @PostMapping("/{id}/shared-services")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Assign an HR / Payroll / Finance / IT team to a company")
    public ResponseEntity<SharedServiceResponse> assignShared(
            @PathVariable UUID id, @Valid @RequestBody AssignSharedServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sharedService.assign(id, request));
    }

    @GetMapping("/{id}/shared-services")
    @PreAuthorize("hasAuthority('COMPANY_READ')")
    @Operation(summary = "List shared-service assignments; optionally filter by team type")
    public List<SharedServiceResponse> listShared(
            @PathVariable UUID id, @RequestParam(required = false) TeamType teamType) {
        return sharedService.list(id, teamType);
    }

    @PatchMapping("/{id}/shared-services/{ssId}/retire")
    @PreAuthorize("hasAuthority('COMPANY_WRITE')")
    @Operation(summary = "Retire a shared-service assignment by setting its effective_to")
    public SharedServiceResponse retireShared(
            @PathVariable UUID id,
            @PathVariable UUID ssId,
            @Valid @RequestBody RetireRequest request) {
        return sharedService.retire(id, ssId, request.effectiveTo());
    }
}
