package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.PayrollCollaborationScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePayrollCollaborationRequest(
        @NotNull UUID clientId,
        @NotNull UUID providerId,
        @NotNull PayrollCollaborationScope scope,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Positive Integer slaDays) {}
