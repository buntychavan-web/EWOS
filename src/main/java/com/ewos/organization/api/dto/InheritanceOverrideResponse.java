package com.ewos.organization.api.dto;

import com.ewos.organization.domain.InheritableKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Effective-dated inheritance override on a node")
public record InheritanceOverrideResponse(
        UUID id,
        UUID nodeId,
        InheritableKind inheritableKind,
        UUID overrideRef,
        String overrideLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
