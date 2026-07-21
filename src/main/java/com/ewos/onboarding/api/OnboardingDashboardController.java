package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.OnboardingDashboardResponse;
import com.ewos.onboarding.api.dto.OnboardingReportRowResponse;
import com.ewos.onboarding.application.OnboardingDashboardService;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding Dashboard", description = "Aggregate reads for HR dashboards + reports")
public class OnboardingDashboardController {

    private final OnboardingDashboardService dashboards;

    public OnboardingDashboardController(OnboardingDashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "Aggregate plan counts by status for a company")
    public OnboardingDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return dashboards.dashboard(tenantId, companyId);
    }

    @GetMapping("/reports/by-status")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "Per-employee onboarding report filtered by plan status")
    public List<OnboardingReportRowResponse> report(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam OnboardingPlanStatus status) {
        return dashboards.reportByStatus(tenantId, companyId, status);
    }
}
