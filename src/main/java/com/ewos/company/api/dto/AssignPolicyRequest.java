package com.ewos.company.api.dto;

import com.ewos.company.domain.PolicyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AssignPolicyRequest(
        @NotNull PolicyType policyType,
        @NotNull UUID policyRef,
        @Size(max = 255) String policyLabel,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
