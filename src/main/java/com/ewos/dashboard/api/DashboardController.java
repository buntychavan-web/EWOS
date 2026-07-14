package com.ewos.dashboard.api;

import com.ewos.common.exception.ApiError;
import com.ewos.dashboard.api.dto.DashboardSummaryResponse;
import com.ewos.dashboard.application.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Aggregate counters powering the dashboard header")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_READ')")
    @Operation(
            summary = "Aggregate counters for the dashboard",
            description =
                    "Returns non-deleted counts for employees, users, departments, and roles in a"
                            + " single database round trip. Employees + Departments return 0 until"
                            + " those modules ship.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Aggregate counts",
                content =
                        @Content(
                                schema = @Schema(implementation = DashboardSummaryResponse.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid credentials",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks DASHBOARD_READ",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public DashboardSummaryResponse summary() {
        return dashboardService.summary();
    }
}
