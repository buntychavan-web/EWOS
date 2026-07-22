package com.ewos.succession.api.dto;

import com.ewos.succession.domain.ReadinessLevel;
import java.util.UUID;

public record CandidateResponse(
        UUID id,
        UUID planId,
        UUID employeeId,
        int priority,
        ReadinessLevel readiness,
        String notes) {}
