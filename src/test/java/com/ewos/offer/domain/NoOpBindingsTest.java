package com.ewos.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import com.ewos.offer.infrastructure.bgv.NoOpBackgroundVerificationService;
import com.ewos.offer.infrastructure.employeeid.SequentialEmployeeIdGenerator;
import com.ewos.offer.infrastructure.medical.NoOpMedicalCheckService;
import com.ewos.offer.infrastructure.notify.NoOpOfferNotifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoOpBindingsTest {

    @Test
    void bgvNoOps() {
        NoOpBackgroundVerificationService bgv = new NoOpBackgroundVerificationService();
        assertThat(bgv.initiate(new PreboardingTaskInstance())).isNull();
        bgv.cancel(new PreboardingTaskInstance());
        assertThat(bgv.providerId()).isEqualTo("noop-bgv");
    }

    @Test
    void medicalNoOps() {
        NoOpMedicalCheckService medical = new NoOpMedicalCheckService();
        assertThat(medical.initiate(new PreboardingTaskInstance())).isNull();
        medical.cancel(new PreboardingTaskInstance());
        assertThat(medical.providerId()).isEqualTo("noop-medical");
    }

    @Test
    void notifierNoOps() {
        NoOpOfferNotifier notifier = new NoOpOfferNotifier();
        notifier.notifyOfferAccepted(new Offer());
        notifier.notifyOfferDeclined(new Offer());
        notifier.notifyOfferExtended(new Offer());
        notifier.notifyOfferRevised(new Offer(), new Offer());
        notifier.notifyOfferExpired(new Offer());
        notifier.notifyOfferWithdrawn(new Offer());
        notifier.notifyCandidateJoined(new Offer());
        assertThat(notifier.providerId()).isEqualTo("noop-offer-notifier");
    }

    @Test
    void employeeIdGeneratorProducesToken() {
        SequentialEmployeeIdGenerator gen = new SequentialEmployeeIdGenerator();
        String id = gen.generate(UUID.randomUUID(), UUID.randomUUID());
        assertThat(id).matches("^EMP-\\d{6}-\\d{6}$");
        assertThat(gen.providerId()).isEqualTo("sequential-employee-id");
    }
}
