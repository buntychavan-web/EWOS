package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(
        description =
                "Split this node into one or more new sibling nodes. Each new node inherits the "
                        + "source's parent and level unless overridden. A SPLIT_FROM version is "
                        + "recorded on each new node linking it to the source.")
public record SplitNodeRequest(
        @NotEmpty @Valid List<NewNodeSpec> newNodes,
        @NotNull LocalDate effectiveFrom,
        @Schema(description = "Deactivate the source node after the split")
                boolean deactivateSource,
        @Size(max = 500) String notes) {}
