package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(
        description =
                "Merge this node into a target node. The source node is deactivated; a MERGED_INTO "
                        + "version is recorded on both sides so history is preserved.")
public record MergeNodeRequest(
        @NotNull UUID targetNodeId,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String notes) {}
