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

    /**
     * Reminder ping to the candidate that the extended offer is still open. Deployments plug in a
     * schedule; the API exposes an on-demand trigger too.
     */
    void notifyOfferReminder(Offer offer);

    /**
     * Reminder to the checklist owner that a pre-boarding task is due / overdue. Deployments plug
     * in the schedule and channel; the API exposes an on-demand trigger.
     */
    void notifyPreboardingTaskReminder(Offer offer, String taskName);

    String providerId();
}
