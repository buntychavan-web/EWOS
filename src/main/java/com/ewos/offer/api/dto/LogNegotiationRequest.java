package com.ewos.offer.api.dto;

import com.ewos.offer.domain.NegotiationParty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LogNegotiationRequest(
        @NotNull NegotiationParty proposedBy,
        String proposedChangesJson,
        @Size(max = 4000) String notes) {}
