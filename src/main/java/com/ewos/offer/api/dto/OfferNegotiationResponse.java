package com.ewos.offer.api.dto;

import com.ewos.offer.domain.NegotiationParty;
import java.time.Instant;
import java.util.UUID;

public record OfferNegotiationResponse(
        UUID id,
        UUID offerId,
        NegotiationParty proposedBy,
        String proposedChangesJson,
        String notes,
        Instant submittedAt,
        Instant respondedAt,
        Boolean accepted,
        UUID resultingOfferId) {}
