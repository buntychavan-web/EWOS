package com.ewos.organization.domain.events;

import com.ewos.organization.domain.OrganizationUnitStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain-event record emitted whenever an {@code organization_units} row changes. The
 * same payload is dispatched on the Spring {@code ApplicationEvent} bus AND serialized to the Kafka
 * topic {@code ewos.organization.unit} so downstream services (analytics, notification, search
 * index) can react without a synchronous coupling to the write path.
 */
public record OrganizationUnitEvent(
        OrganizationUnitEventType eventType,
        UUID unitId,
        UUID tenantId,
        UUID companyId,
        UUID unitTypeId,
        UUID parentId,
        String code,
        String name,
        OrganizationUnitStatus status,
        UUID actorId,
        Instant occurredAt) {}
