package com.ewos.organization.infrastructure.messaging;

/**
 * Kafka topic names owned by the Organization module. Topics follow the platform-wide convention
 * {@code ewos.<module>.<aggregate>}.
 */
public final class OrganizationTopics {

    /**
     * Domain events emitted by the {@link com.ewos.organization.domain.OrganizationUnit} aggregate.
     */
    public static final String UNIT = "ewos.organization.unit";

    private OrganizationTopics() {}
}
