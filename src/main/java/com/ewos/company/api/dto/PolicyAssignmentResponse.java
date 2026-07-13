package com.ewos.company.api.dto;

import com.ewos.company.domain.PolicyType;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyAssignmentResponse(
        UUID id,
        UUID companyId,
        PolicyType policyType,
        UUID policyRef,
        String policyLabel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        long version) {}
