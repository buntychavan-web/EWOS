package com.ewos.offer.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering offer + pre-boarding lifecycle changes. */
public record OfferEvent(
        OfferEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID offerId,
        String offerNumber,
        UUID applicationId,
        UUID candidateId,
        UUID checklistId,
        UUID taskId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
