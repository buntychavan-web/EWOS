package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Create a Person")
public record CreatePersonRequest(
        @Schema(description = "Optional tenant id; defaults to DEFAULT") UUID tenantId,
        @NotNull @Valid PersonProfile profile,
        @Schema(
                        description =
                                "If duplicate detection returns matches, this must be true (plus"
                                        + " PERSON_DUPLICATE_OVERRIDE) to force creation")
                boolean overrideDuplicates) {}
