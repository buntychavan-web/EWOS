package com.ewos.employee.api;

import com.ewos.employee.api.dto.MssDashboardResponse;
import com.ewos.employee.application.MssDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — MSS Dashboard (PRD §4.2). Direct reports only; supports {@code actingForEmployeeId}
 * for a peer who has actively delegated to the caller, same as {@code
 * ManagerApprovalsController}/{@code MssTeamController}.
 */
@RestController
@RequestMapping("/api/v1/manager-self-service/dashboard")
@Tag(
        name = "Manager Self-Service Dashboard",
        description = "The caller's team summary, attendance snapshot, and upcoming team leave")
public class MssDashboardController {

    private final MssDashboardService service;

    public MssDashboardController(MssDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The caller's MSS dashboard")
    public MssDashboardResponse dashboard(
            @RequestParam(required = false) UUID actingForEmployeeId) {
        return service.dashboard(actingForEmployeeId);
    }
}
