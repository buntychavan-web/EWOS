package com.ewos.offer.domain;

/**
 * Contract for candidate + recruiter + hiring-manager + HR notifications throughout the offer
 * lifecycle and pre-boarding. Default in-tree binding no-ops; real senders plug in as a
 * {@code @Primary} bean.
 */
public interface OfferNotifier {

    void notifyOfferExtended(Offer offer);

    void notifyOfferAccepted(Offer offer);

    void notifyOfferDeclined(Offer offer);

    void notifyOfferRevised(Offer previous, Offer revised);

    void notifyOfferExpired(Offer offer);

    void notifyOfferWithdrawn(Offer offer);

    /** Called when a checklist reaches JOINED — signals HR + hiring manager + IT + admin. */
    void notifyCandidateJoined(Offer offer);

    String providerId();
}
