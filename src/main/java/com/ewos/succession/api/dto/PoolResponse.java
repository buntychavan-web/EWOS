package com.ewos.succession.api.dto;

import com.ewos.succession.domain.TalentTier;
import java.util.UUID;

public record PoolResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        TalentTier tier,
        boolean active) {}
