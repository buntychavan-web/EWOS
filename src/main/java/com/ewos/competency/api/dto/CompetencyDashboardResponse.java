package com.ewos.competency.api.dto;

public record CompetencyDashboardResponse(
        long activeCompetencies,
        long draftPlans,
        long activePlans,
        long completedPlans,
        long cancelledPlans) {}
