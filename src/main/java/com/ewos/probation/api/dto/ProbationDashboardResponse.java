package com.ewos.probation.api.dto;

public record ProbationDashboardResponse(
        long inProbation,
        long extended,
        long pendingApproval,
        long confirmed,
        long terminated,
        long cancelled,
        long dueWithin30Days,
        long overdue) {}
