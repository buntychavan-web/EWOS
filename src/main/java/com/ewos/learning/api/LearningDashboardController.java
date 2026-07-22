package com.ewos.learning.api;

import com.ewos.learning.api.dto.LearningDashboardResponse;
import com.ewos.learning.application.LearningDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
@Tag(name = "Learning Dashboard", description = "Aggregate learning module counts")
public class LearningDashboardController {

    private final LearningDashboardService dashboards;

    public LearningDashboardController(LearningDashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('LEARNING_READ')")
    @Operation(summary = "Aggregate learning module counts for a company")
    public LearningDashboardResponse dashboard(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestParam UUID companyId) {
        return dashboards.dashboard(tenantId, companyId);
    }
}
