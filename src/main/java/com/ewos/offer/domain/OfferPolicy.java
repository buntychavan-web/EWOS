package com.ewos.offer.domain;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Lifecycle + integrity guards for {@link Offer}. */
@Component
public class OfferPolicy {

    private static final Set<OfferStatus> TERMINAL =
            EnumSet.of(
                    OfferStatus.REJECTED,
                    OfferStatus.ACCEPTED,
                    OfferStatus.DECLINED,
                    OfferStatus.REVISED,
                    OfferStatus.EXPIRED,
                    OfferStatus.WITHDRAWN);

    private final Clock clock;

    public OfferPolicy() {
        this(Clock.systemUTC());
    }

    OfferPolicy(Clock clock) {
        this.clock = clock;
    }

    /** Enforce {@code totalCtc == baseSalary + variable + one-time + hiring + retention}. */
    public void assertCompensationCoherent(Offer o) {
        if (o.getBaseSalary() == null || o.getBaseSalary().signum() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Base salary is required and must be non-negative");
        }
        if (o.getTotalCtc() == null || o.getTotalCtc().signum() < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Total CTC is required and must be non-negative");
        }
        BigDecimal computed = o.compensation().totalCtc();
        if (computed.compareTo(o.getTotalCtc()) != 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Total CTC ("
                            + o.getTotalCtc()
                            + ") does not match sum of components ("
                            + computed
                            + ")");
        }
    }

    public void assertEditable(Offer o) {
        if (o.getStatus() != OfferStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer can only be edited in DRAFT (current: " + o.getStatus() + ")");
        }
    }

    public void assertSubmittable(Offer o) {
        if (o.getStatus() != OfferStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be DRAFT to submit (current: " + o.getStatus() + ")");
        }
        assertCompensationCoherent(o);
    }

    public void assertDecidable(Offer o) {
        if (o.getStatus() != OfferStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be PENDING_APPROVAL to decide (current: " + o.getStatus() + ")");
        }
    }

    public void assertExtendable(Offer o) {
        if (o.getStatus() != OfferStatus.APPROVED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be APPROVED to extend (current: " + o.getStatus() + ")");
        }
    }

    public void assertAcceptable(Offer o) {
        if (o.getStatus() != OfferStatus.EXTENDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be EXTENDED to accept (current: " + o.getStatus() + ")");
        }
        if (o.getExpiresAt() != null && !o.getExpiresAt().isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.CONFLICT, "Offer has expired");
        }
    }

    public void assertDeclinable(Offer o) {
        if (o.getStatus() != OfferStatus.EXTENDED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be EXTENDED to decline (current: " + o.getStatus() + ")");
        }
    }

    public void assertRevisable(Offer o) {
        if (o.getStatus() != OfferStatus.EXTENDED
                && o.getStatus() != OfferStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be EXTENDED or PENDING_APPROVAL to revise (current: "
                            + o.getStatus()
                            + ")");
        }
    }

    public void assertWithdrawable(Offer o) {
        if (TERMINAL.contains(o.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer is already terminal (current: " + o.getStatus() + ")");
        }
    }

    public void assertNegotiable(Offer o) {
        if (o.getStatus() != OfferStatus.EXTENDED
                && o.getStatus() != OfferStatus.PENDING_APPROVAL) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Offer must be EXTENDED or PENDING_APPROVAL to log negotiation (current: "
                            + o.getStatus()
                            + ")");
        }
    }

    public boolean isTerminal(OfferStatus status) {
        return TERMINAL.contains(status);
    }

    public boolean isExpired(Offer o) {
        return o.getStatus() == OfferStatus.EXTENDED
                && o.getExpiresAt() != null
                && !o.getExpiresAt().isAfter(Instant.now(clock));
    }
}
