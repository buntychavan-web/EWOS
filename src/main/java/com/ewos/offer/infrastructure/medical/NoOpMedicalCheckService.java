package com.ewos.offer.infrastructure.medical;

import com.ewos.offer.domain.MedicalCheckService;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import org.springframework.stereotype.Component;

/** Default {@link MedicalCheckService} binding — no external calls. */
@Component
public class NoOpMedicalCheckService implements MedicalCheckService {

    private static final String PROVIDER = "noop-medical";

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
