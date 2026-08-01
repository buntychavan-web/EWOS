package com.ewos.performance.api.dto;

import java.util.UUID;

public record OrgUnitProgressResponse(
        UUID orgUnitId,
        String orgUnitCode,
        String orgUnitName,
        long totalAppraisals,
        long pendingSelf,
        long pendingManager,
        long pendingReviewer,
        long inCalibration,
        long pendingApproval,
        long finalised,
        long cancelled,
        double completionPercent) {}
