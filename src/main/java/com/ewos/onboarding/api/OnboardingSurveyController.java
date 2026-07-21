package com.ewos.onboarding.api;

import com.ewos.onboarding.api.dto.OnboardingSurveyResponse;
import com.ewos.onboarding.api.dto.SubmitOnboardingSurveyRequest;
import com.ewos.onboarding.application.OnboardingSurveyService;
import com.ewos.onboarding.domain.OnboardingSurveyType;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding/plans/{planId}/surveys")
@Tag(name = "Onboarding Surveys", description = "30/60/90-day + open-ended feedback per plan")
public class OnboardingSurveyController {

    private final OnboardingSurveyService surveys;

    public OnboardingSurveyController(OnboardingSurveyService surveys) {
        this.surveys = surveys;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ONBOARDING_SURVEY')")
    @Operation(summary = "Submit or update the survey for a given cadence")
    public OnboardingSurveyResponse submit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID planId,
            @Valid @RequestBody SubmitOnboardingSurveyRequest req) {
        return surveys.submit(tenantId, planId, req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "List surveys submitted for a plan")
    public List<OnboardingSurveyResponse> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID planId) {
        return surveys.listForPlan(tenantId, planId);
    }

    @GetMapping("/{surveyType}")
    @PreAuthorize("hasAuthority('ONBOARDING_READ')")
    @Operation(summary = "Fetch a single survey by type")
    public OnboardingSurveyResponse get(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID planId,
            @PathVariable OnboardingSurveyType surveyType) {
        return surveys.getFor(tenantId, planId, surveyType);
    }
}
