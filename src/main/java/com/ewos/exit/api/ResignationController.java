package com.ewos.exit.api;

import com.ewos.exit.api.dto.AcceptResignationRequest;
import com.ewos.exit.api.dto.ApplyBuyoutRequest;
import com.ewos.exit.api.dto.ApplyNoticeRecoveryRequest;
import com.ewos.exit.api.dto.ApproveEarlyReleaseRequest;
import com.ewos.exit.api.dto.CompleteExitRequest;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.ExitDashboardResponse;
import com.ewos.exit.api.dto.ExtendNoticeRequest;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.StartGardenLeaveRequest;
import com.ewos.exit.api.dto.WaiveNoticeRequest;
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
    @Operation(
            summary = "Record a resignation or separation on an employee's behalf",
            description =
                    "For HR-initiated, manager-initiated, retirement, termination, death, or"
                            + " absconding cases. Employee self-service submissions use"
                            + " POST /api/v1/exit/self-service/resignations instead.")
    public ResponseEntity<ResignationResponse> submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateResignationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exit.submit(tenantId, req));
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

    @PostMapping("/{id}/notice-recovery")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Recover pay from the employee for a notice-period shortfall")
    public ResignationResponse applyNoticeRecovery(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ApplyNoticeRecoveryRequest req) {
        return exit.applyNoticeRecovery(tenantId, id, req);
    }

    @PostMapping("/{id}/notice-waiver")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Waive the remaining notice period")
    public ResignationResponse waiveNotice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody WaiveNoticeRequest req) {
        return exit.waiveNotice(tenantId, id, req);
    }

    @PostMapping("/{id}/garden-leave")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Record a garden-leave window within the notice period")
    public ResignationResponse startGardenLeave(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody StartGardenLeaveRequest req) {
        return exit.startGardenLeave(tenantId, id, req);
    }

    @PostMapping("/{id}/notice-extension")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Extend the notice period end date")
    public ResignationResponse extendNotice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ExtendNoticeRequest req) {
        return exit.extendNotice(tenantId, id, req);
    }

    @PostMapping("/{id}/early-release")
    @PreAuthorize("hasAuthority('EXIT_APPROVE')")
    @Operation(summary = "Approve an earlier-than-scheduled last working day")
    public ResignationResponse approveEarlyRelease(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody ApproveEarlyReleaseRequest req) {
        return exit.approveEarlyRelease(tenantId, id, req);
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
