package com.ewos.competency.api;

import com.ewos.competency.api.dto.ActionResponse;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.competency.application.DevelopmentPlanSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24D — Employee Self-Service over Development Plans. Requires only authentication (no
 * {@code COMPETENCY_*} permission): every method resolves the caller's own employee record and is
 * scoped to it, or is rejected with 403 if the target plan/action isn't the caller's own. Mirrors
 * {@code GoalSelfServiceController} exactly.
 */
@RestController
@RequestMapping("/api/v1/development-plans/self-service")
@Tag(name = "Development Plans Self-Service", description = "An employee's own development plan")
public class DevelopmentPlanSelfServiceController {

    private final DevelopmentPlanSelfService service;

    public DevelopmentPlanSelfServiceController(DevelopmentPlanSelfService service) {
        this.service = service;
    }

    @GetMapping("/plans")
    @Operation(summary = "The caller's own development plans")
    public List<PlanResponse> myPlans() {
        return service.myPlans();
    }

    @GetMapping("/plans/{planId}/actions")
    @Operation(summary = "Actions on one of the caller's own development plans")
    public List<ActionResponse> myPlanActions(@PathVariable UUID planId) {
        return service.myPlanActions(planId);
    }

    @PostMapping("/actions/{actionId}/complete")
    @Operation(summary = "Mark one of the caller's own development plan actions complete")
    public ActionResponse completeMyAction(@PathVariable UUID actionId) {
        return service.completeMyAction(actionId);
    }
}
