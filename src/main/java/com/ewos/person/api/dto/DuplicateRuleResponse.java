package com.ewos.person.api.dto;

import com.ewos.person.domain.DuplicateRuleKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Configured duplicate-detection rule for a tenant")
public record DuplicateRuleResponse(
        UUID id,
        UUID tenantId,
        DuplicateRuleKind ruleKind,
        boolean enabled,
        int weight,
        long version) {}
