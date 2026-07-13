package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Update Person profile — creates a new effective-dated version")
public record UpdatePersonProfileRequest(
        @NotNull @Valid PersonProfile profile,
        @Schema(description = "Optional approver user id captured on the new version")
                UUID approvedBy) {}
