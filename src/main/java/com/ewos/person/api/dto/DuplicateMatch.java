package com.ewos.person.api.dto;

import com.ewos.person.domain.DuplicateRuleKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "A duplicate candidate + why it matched")
public record DuplicateMatch(
        UUID personId,
        String groupPersonId,
        String matchedName,
        DuplicateRuleKind ruleKind,
        String matchedValue,
        int weight) {}
