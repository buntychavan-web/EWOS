package com.ewos.onboarding.api.dto;

import java.util.List;

/** Aggregate view for HR dashboards. */
public record OnboardingDashboardResponse(
        long plansPlanned,
        long plansInProgress,
        long plansCompleted,
        long plansCancelled,
        List<PlanStatusBucket> byStatus) {

    public record PlanStatusBucket(String status, long count) {}
}
