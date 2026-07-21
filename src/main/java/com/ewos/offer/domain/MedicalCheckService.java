package com.ewos.offer.domain;

import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;

/** Framework contract for pre-employment medical checks. Default in-tree binding no-ops. */
public interface MedicalCheckService {

    String initiate(PreboardingTaskInstance task);

    void cancel(PreboardingTaskInstance task);

    String providerId();
}
