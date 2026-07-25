package com.ewos.tenancy.domain;

/**
 * Lifecycle status. Values match the existing platform-wide convention (see
 * OrganizationUnitStatus).
 */
public enum ClientStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
