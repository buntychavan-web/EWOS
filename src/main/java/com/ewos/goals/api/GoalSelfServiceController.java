package com.ewos.goals.api;

import com.ewos.goals.api.dto.GoalProgressResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.api.dto.ProgressUpdateRequest;
import com.ewos.goals.application.GoalSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24D — Employee Self-Service over Goals. Requires only authentication (no {@code GOAL_*}
 * permission): every method resolves the caller's own employee record and is scoped to it, or is
 * rejected with 403 if the target goal isn't the caller's own. Mirrors {@code
 * PerformanceSelfServiceController} (Sprint 24A) exactly.
 */
@RestController
@RequestMapping("/api/v1/goals/self-service")
@Tag(name = "Goals Self-Service", description = "An employee's own goals")
public class GoalSelfServiceController {

    private final GoalSelfService service;

    public GoalSelfServiceController(GoalSelfService service) {
        this.service = service;
    }

    @GetMapping("/goals")
    @Operation(summary = "The caller's own goals")
    public List<GoalResponse> myGoals() {
        return service.myGoals();
    }

    @PostMapping("/goals/{id}/progress")
    @Operation(summary = "Record progress against one of the caller's own goals")
    public GoalProgressResponse recordMyProgress(
            @PathVariable UUID id, @Valid @RequestBody ProgressUpdateRequest req) {
        return service.recordMyProgress(id, req);
    }

    @PostMapping("/goals/{id}/submit-review")
    @Operation(summary = "Submit one of the caller's own goals for review")
    public GoalResponse submitMyGoalForReview(@PathVariable UUID id) {
        return service.submitMyGoalForReview(id);
    }
}
