package com.ewos.leave.api;

import com.ewos.leave.api.dto.AdjustBalanceRequest;
import com.ewos.leave.api.dto.AllocationResponse;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.UpsertAllocationRequest;
import com.ewos.leave.application.LeaveBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
@RequestMapping("/api/v1/leave")
@Tag(name = "Leave Balances", description = "Allocations and running balances per employee")
public class LeaveBalanceController {

    private final LeaveBalanceService service;

    public LeaveBalanceController(LeaveBalanceService service) {
        this.service = service;
    }

    @PostMapping("/allocations")
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Create or update the yearly allocation for an employee and leave type")
    public AllocationResponse upsertAllocation(
            @Valid @RequestBody UpsertAllocationRequest request) {
        return service.upsertAllocation(request);
    }

    @GetMapping("/allocations/employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "List allocations for an employee in a year")
    public List<AllocationResponse> allocationsForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam int year) {
        return service.allocationsForEmployee(tenantId, employeeId, year);
    }

    @PostMapping("/balances/adjust")
    @PreAuthorize("hasAuthority('LEAVE_ADMIN')")
    @Operation(summary = "Apply a manual adjustment (positive or negative) to an employee balance")
    public BalanceResponse adjust(@Valid @RequestBody AdjustBalanceRequest request) {
        return service.adjust(request);
    }

    @GetMapping("/balances/employee/{employeeId}")
    @PreAuthorize("hasAuthority('LEAVE_READ')")
    @Operation(summary = "List balances for an employee in a year")
    public List<BalanceResponse> balancesForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam int year) {
        return service.balancesForEmployee(tenantId, employeeId, year);
    }
}
