package com.ewos.company.api.dto;

import com.ewos.company.domain.PolicyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Payload for creating a company. Supports blank / clone-existing modes.")
public record CreateCompanyRequest(
        @Schema(description = "Owning tenant id.") UUID tenantId,
        @NotBlank
                @Size(max = 50)
                @Pattern(
                        regexp = "^[A-Z0-9_-]+$",
                        message = "code must be uppercase alphanumeric with dashes/underscores")
                String code,
        @NotNull @Valid CompanyProfile profile,
        @Schema(description = "Optional source company to clone from (blank = new company).")
                UUID cloneFromId,
        @Schema(description = "If cloning, which policy types to carry over as active references.")
                Set<PolicyType> clonePolicyTypes) {}
