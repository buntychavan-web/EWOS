package com.ewos.leave.api;

import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.api.dto.SelfLeaveRequestRequest;
import com.ewos.leave.application.LeaveSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 3 — Employee Self-Service over Leave. Requires only authentication (no LEAVE_* permission):
 * every method resolves the caller's own employee record and is scoped to it. Mirrors {@code
 * EmployeeController.me()} (Sprint 1.3) exactly for how "no linked employee" is handled.
 */
@RestController
@RequestMapping("/api/v1/leave/self-service")
@Tag(name = "Leave Self-Service", description = "An employee's own leave requests and balances")
public class LeaveSelfServiceController {

    private final LeaveSelfService service;

    public LeaveSelfServiceController(LeaveSelfService service) {
        this.service = service;
    }

    @GetMapping("/leave-types")
    @Operation(summary = "Catalogue of leave types available to apply for")
    public List<LeaveTypeResponse> leaveTypes() {
        return service.leaveTypes();
    }

    @GetMapping("/requests")
    @Operation(summary = "The caller's own leave requests, most-recent-first")
    public List<LeaveRequestResponse> myRequests() {
        return service.myRequests();
    }

    @PostMapping("/requests")
    @Operation(summary = "Create a DRAFT leave request against the caller's own employee record")
    public ResponseEntity<LeaveRequestResponse> createMyRequest(
            @Valid @RequestBody SelfLeaveRequestRequest request) {
        LeaveRequestResponse created = service.createMyRequest(request);
        return ResponseEntity.created(
                        URI.create("/api/v1/leave/self-service/requests/" + created.id()))
                .body(created);
    }

    @PostMapping("/requests/{id}/submit")
    @Operation(
            summary =
                    "Submit the caller's own DRAFT request for approval; the tenant's active"
                            + " leave-approval workflow is resolved automatically")
    public LeaveRequestResponse submitMyRequest(@PathVariable UUID id) {
        return service.submitMyRequest(id);
    }

    @PostMapping("/requests/{id}/cancel")
    @Operation(summary = "Cancel the caller's own DRAFT or SUBMITTED request")
    public LeaveRequestResponse cancelMyRequest(@PathVariable UUID id) {
        return service.cancelMyRequest(id);
    }

    @GetMapping("/balances")
    @Operation(summary = "The caller's own leave balances for a year (defaults to current year)")
    public List<BalanceResponse> myBalances(@RequestParam(required = false) Integer year) {
        return service.myBalances(year);
    }

    @GetMapping("/reports/pending")
    @Operation(summary = "SUBMITTED leave requests from the caller's own direct reports, paginated")
    public Page<LeaveRequestResponse> pendingForMyReports(
            @ParameterObject @PageableDefault(size = 20, sort = "startDate") Pageable pageable) {
        return service.pendingForMyReports(pageable);
    }
}
