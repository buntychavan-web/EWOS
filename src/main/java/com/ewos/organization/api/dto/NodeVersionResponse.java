package com.ewos.organization.api.dto;

import com.ewos.organization.domain.NodeChangeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Historical structural-change record for an organization node")
public record NodeVersionResponse(
        UUID id,
        UUID nodeId,
        NodeChangeType changeType,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String snapshotCode,
        String snapshotName,
        UUID snapshotParentId,
        UUID snapshotLevelId,
        UUID relatedNodeId,
        String notes,
        Instant createdAt,
        UUID createdBy) {}
