package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Move a node under a new parent — recorded as a MOVED version")
public record MoveNodeRequest(
        @Schema(description = "New parent node id; null makes the node a root")
                UUID newParentNodeId,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String notes) {}
