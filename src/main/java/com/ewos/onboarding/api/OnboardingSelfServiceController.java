package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.onboarding.application.OnboardingSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 23A — Employee Self-Service over Onboarding. Requires only authentication (no ONBOARDING_*
 * permission): every method resolves the caller's own employee record and is scoped to it. Mirrors
 * {@code LeaveSelfServiceController} (Sprint 3) exactly for how "no linked employee" is handled.
 */
@RestController
@RequestMapping("/api/v1/onboarding/self-service")
@Tag(name = "Onboarding Self-Service", description = "A new hire's own onboarding plan and tasks")
public class OnboardingSelfServiceController {

    private final OnboardingSelfService service;

    public OnboardingSelfServiceController(OnboardingSelfService service) {
        this.service = service;
    }

    @GetMapping("/plan")
    @Operation(summary = "The caller's own onboarding plan")
    public OnboardingPlanResponse myPlan() {
        return service.myPlan();
    }

    @GetMapping("/tasks")
    @Operation(summary = "The tasks on the caller's own onboarding plan, in sort order")
    public List<OnboardingTaskInstanceResponse> myTasks() {
        return service.myTasks();
    }
}
