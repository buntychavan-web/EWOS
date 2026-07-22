package com.ewos.goals.api.dto;

import com.ewos.goals.domain.GoalType;
import java.math.BigDecimal;
import java.util.UUID;

public record GoalLibraryItemResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        GoalType goalType,
        String category,
        BigDecimal defaultWeightage,
        String defaultTarget,
        String unitOfMeasure,
        boolean active) {}
