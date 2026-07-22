package com.ewos.learning.api.dto;

public record LearningDashboardResponse(
        long activeCourses,
        long scheduledSessions,
        long nominated,
        long enrolled,
        long inProgress,
        long completed,
        long withdrawn,
        long activeCertifications,
        long expiringSoon) {}
