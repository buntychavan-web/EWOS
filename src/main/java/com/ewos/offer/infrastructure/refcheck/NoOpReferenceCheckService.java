package com.ewos.offer.infrastructure.refcheck;

import com.ewos.offer.domain.ReferenceCheckService;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import org.springframework.stereotype.Component;

/** Default {@link ReferenceCheckService} binding — no external calls. */
@Component
public class NoOpReferenceCheckService implements ReferenceCheckService {

    private static final String PROVIDER = "noop-reference-check";

    @Override
    public String initiate(PreboardingTaskInstance task) {
        return null;
    }

    @Override
    public void cancel(PreboardingTaskInstance task) {
        // no-op
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
