package com.ewos.offer.domain;

import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;

/**
 * Framework contract for reference-check vendors (Xref, Checkster, HireRight, ...). Default in-tree
 * binding no-ops so a checklist REFERENCE_CHECK task simply waits for manual completion.
 * Deployments plug in a {@code @Primary} bean to auto-initiate vendor runs.
 */
public interface ReferenceCheckService {

    String initiate(PreboardingTaskInstance task);

    void cancel(PreboardingTaskInstance task);

    String providerId();
}
