package com.ewos.organization.api.dto;

import com.ewos.organization.domain.InheritableKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Add or extend an inheritance override on a node")
public record SetInheritanceOverrideRequest(
        @NotNull InheritableKind inheritableKind,
        @NotNull UUID overrideRef,
        @Size(max = 255) String overrideLabel,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
