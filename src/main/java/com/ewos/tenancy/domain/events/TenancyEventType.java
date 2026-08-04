package com.ewos.tenancy.domain.events;

/** Tenant-access-grant lifecycle event codes. See {@code TenancyNotificationEventListener}. */
public enum TenancyEventType {
    ACCESS_GRANTED,
    ACCESS_REVOKED
}
