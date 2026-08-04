package com.ewos.tenancy.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering {@code TenantAccessGrant} issuance/revocation — a security-sensitive action
 * (granting a user access to a tenant that isn't their own) that previously produced no alert to
 * anyone. {@code userId} is the grant recipient; {@code grantedTenantId} is the tenant they were
 * granted (or had revoked) access to — deliberately not the notification's own {@code tenantId},
 * since the recipient should see this in their normal inbox, scoped to their own home tenant (see
 * {@code TenancyNotificationEventListener}).
 */
public record TenancyEvent(
        TenancyEventType eventType,
        UUID userId,
        UUID grantedTenantId,
        UUID actorId,
        Instant occurredAt) {}
