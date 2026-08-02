package com.ewos.identity.domain.events;

/** Identity/account lifecycle event codes. See {@code IdentityNotificationEventListener}. */
public enum IdentityEventType {
    PASSWORD_RESET_BY_ADMIN,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED
}
