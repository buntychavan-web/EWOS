package com.ewos.offer.infrastructure.notify;

import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNotifier;
import org.springframework.stereotype.Component;

/** Default {@link OfferNotifier} binding — no external calls. */
@Component
public class NoOpOfferNotifier implements OfferNotifier {

    private static final String PROVIDER = "noop-offer-notifier";

    @Override
    public void notifyOfferExtended(Offer offer) {
        // no-op
    }

    @Override
    public void notifyOfferAccepted(Offer offer) {
        // no-op
    }

    @Override
    public void notifyOfferDeclined(Offer offer) {
        // no-op
    }

    @Override
    public void notifyOfferRevised(Offer previous, Offer revised) {
        // no-op
    }

    @Override
    public void notifyOfferExpired(Offer offer) {
        // no-op
    }

    @Override
    public void notifyOfferWithdrawn(Offer offer) {
        // no-op
    }

    @Override
    public void notifyCandidateJoined(Offer offer) {
        // no-op
    }

    @Override
    public void notifyOfferReminder(Offer offer) {
        // no-op
    }

    @Override
    public void notifyPreboardingTaskReminder(Offer offer, String taskName) {
        // no-op
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
