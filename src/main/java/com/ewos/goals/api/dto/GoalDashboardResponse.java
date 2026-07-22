package com.ewos.goals.api.dto;

public record GoalDashboardResponse(
        long draft,
        long assigned,
        long inProgress,
        long underReview,
        long completed,
        long cancelled,
        long individual,
        long team,
        long department,
        long company) {}
