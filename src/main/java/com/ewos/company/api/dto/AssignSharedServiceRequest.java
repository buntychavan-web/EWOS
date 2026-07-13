package com.ewos.company.api.dto;

import com.ewos.company.domain.TeamType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AssignSharedServiceRequest(
        @NotNull TeamType teamType,
        @NotNull UUID teamRef,
        @Size(max = 255) String teamLabel,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
