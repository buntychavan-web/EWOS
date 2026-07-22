package com.ewos.performance.api.dto;

import java.util.UUID;

public record PerformanceDashboardResponse(
        UUID cycleId,
        long pendingSelf,
        long pendingManager,
        long pendingReviewer,
        long inCalibration,
        long pendingApproval,
        long finalised,
        long cancelled) {}
