package com.ewos.organization.domain;

/**
 * Lifecycle status of an {@link OrganizationUnit}.
 *
 * <p>Values are persisted as strings (see column {@code status} in {@code organization_units});
 * changing them requires a Flyway migration to update in-flight rows.
 */
public enum OrganizationUnitStatus {

    /** Operational. Employees can be assigned; the unit participates in reporting hierarchies. */
    ACTIVE,

    /**
     * Temporarily paused. Read-only. Existing memberships remain, no new assignments allowed. A
     * SUSPENDED unit can be re-activated without a new record.
     */
    SUSPENDED,

    /**
     * End-of-life. {@code effective_to} is set. Assignments must be terminated before a unit can
     * move here. A CLOSED unit is retained for historical reporting and audit; it is never hard
     * deleted.
     */
    CLOSED
}
