package com.ewos.exit.api;

import com.ewos.exit.api.dto.AcceptResignationRequest;
import com.ewos.exit.api.dto.ApplyBuyoutRequest;
import com.ewos.exit.api.dto.CompleteExitRequest;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.ExitDashboardResponse;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.application.ExitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/exit/resignations")
@Tag(name = "Exit — Resignations", description = "Resignation lifecycle, notice, buyout")
public class ResignationController {

    private final ExitService exit;

    public ResignationController(ExitService exit) {
        this.exit = exit;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EXIT_WRITE')")
    @Operation(summary = "Submit a resignation")
    public ResponseEntity<ResignationResponse> submit(
            @Valid @RequestBody CreateResignationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exit.submit(req));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "Fetch a resignation by id")
    public ResignationResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return exit.getResignation(tenantId, id);
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "List an employee's resignation history")
    public List<ResignationResponse> byEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return exit.resignationsForEmployee(tenantId, employeeId);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Accept a resignation")
    public ResignationResponse accept(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) AcceptResignationRequest req) {
        return exit.accept(tenantId, id, req);
    }

    @PostMapping("/{id}/start-notice")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Move an accepted resignation into notice period")
    public ResignationResponse startNotice(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return exit.startNotice(tenantId, id);
    }

    @PostMapping("/{id}/buyout")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Apply a notice-period buyout")
    public ResignationResponse applyBuyout(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ApplyBuyoutRequest req) {
        return exit.applyBuyout(tenantId, id, req);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('EXIT_WRITE')")
    @Operation(summary = "Withdraw a resignation")
    public ResignationResponse withdraw(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return exit.withdraw(tenantId, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Cancel a resignation")
    public ResignationResponse cancel(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID id) {
        return exit.cancel(tenantId, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Complete exit — sets last day + rehire eligibility")
    public ResignationResponse complete(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CompleteExitRequest req) {
        return exit.completeExit(tenantId, id, req);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('EXIT_READ')")
    @Operation(summary = "Aggregate exit + alumni dashboard")
    public ExitDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return exit.dashboard(tenantId, companyId);
    }
}
