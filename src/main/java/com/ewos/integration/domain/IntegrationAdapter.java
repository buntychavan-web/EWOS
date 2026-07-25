package com.ewos.integration.domain;

/**
 * Port for a pluggable outbound transport. Each {@link IntegrationAdapterType} has exactly one
 * implementation, registered by {@link
 * com.ewos.integration.infrastructure.adapter.IntegrationAdapterRegistry}. Implementations never
 * throw for expected failure modes — they report a classified {@link IntegrationAdapterResult}
 * instead, so {@link com.ewos.integration.application.IntegrationExecutionService} has a single,
 * uniform place to decide what happens next (mark the source record SUCCESS/FAILED, record the
 * attempt).
 */
public interface IntegrationAdapter {

    IntegrationAdapterType type();

    IntegrationAdapterResult execute(IntegrationExecutionContext context);
}
