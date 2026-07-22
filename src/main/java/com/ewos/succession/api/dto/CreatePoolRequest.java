package com.ewos.succession.api.dto;

import com.ewos.succession.domain.TalentTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePoolRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 4000) String description,
        TalentTier tier) {}
