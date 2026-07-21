package com.ewos.offer.domain;

import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;

/**
 * Framework contract for background-verification vendors (First Advantage, HireRight, ...). Real
 * deployments plug in a {@code @Primary} bean; the default in-tree binding no-ops.
 */
public interface BackgroundVerificationService {

    /**
     * Start a BGV run for the task. Returns an opaque vendor reference stored on {@link
     * PreboardingTaskInstance#getExternalRef()} for later reconciliation, or {@code null}.
     */
    String initiate(PreboardingTaskInstance task);

    /** Cancel any in-flight BGV run. */
    void cancel(PreboardingTaskInstance task);

    /** Vendor / provider identifier (recorded for audit). */
    String providerId();
}
