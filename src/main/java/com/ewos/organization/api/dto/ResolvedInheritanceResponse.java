package com.ewos.organization.api.dto;

import com.ewos.organization.domain.InheritableKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Result of resolving an inheritable kind for a node")
public record ResolvedInheritanceResponse(
        InheritableKind inheritableKind,
        @Schema(
                        description =
                                "Node id where the value was found. Null when nothing in the chain overrides it "
                                        + "and the resolver would fall through to the company-level assignment.")
                UUID sourceNodeId,
        UUID overrideRef,
        String overrideLabel,
        boolean fromOverride) {}
