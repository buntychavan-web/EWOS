package com.ewos.offer.infrastructure.bgv;

import com.ewos.offer.domain.BackgroundVerificationService;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import org.springframework.stereotype.Component;

/** Default {@link BackgroundVerificationService} binding — no external calls. */
@Component
public class NoOpBackgroundVerificationService implements BackgroundVerificationService {

    private static final String PROVIDER = "noop-bgv";

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
