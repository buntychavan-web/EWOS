package com.ewos.onboarding.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering onboarding lifecycle changes. */
public record OnboardingEvent(
        OnboardingEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID planId,
        UUID employeeId,
        UUID taskId,
        UUID surveyId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
