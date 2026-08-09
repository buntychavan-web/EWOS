package com.ewos.employee.api;

import com.ewos.employee.api.dto.EssDashboardResponse;
import com.ewos.employee.application.EssDashboardService;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — ESS Dashboard (PRD §4.1). {@code employeeId} is always resolved server-side from
 * {@link com.ewos.employee.application.EmployeeContext}, never accepted as a parameter.
 */
@RestController
@RequestMapping("/api/v1/self-service/dashboard")
@Tag(
        name = "Employee Self-Service Dashboard",
        description =
                "The caller's own landing-page aggregate: profile, pending actions, leave,"
                        + " payroll snapshot, and upcoming events")
public class EssDashboardController {

    private final EssDashboardService service;
    private final TenantContext tenantContext;

    public EssDashboardController(EssDashboardService service, TenantContext tenantContext) {
        this.service = service;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @Operation(summary = "The caller's ESS dashboard")
    public EssDashboardResponse dashboard() {
        return service.dashboard(tenantContext.homeTenantId());
    }
}
