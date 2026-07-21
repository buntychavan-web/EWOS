package com.ewos.offer.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OfferPolicyTest {

    private final OfferPolicy policy = new OfferPolicy();

    @Test
    void compensationCoherentPassesForValidTotals() {
        Offer o = coherentOffer();
        assertThatCode(() -> policy.assertCompensationCoherent(o)).doesNotThrowAnyException();
    }

    @Test
    void compensationRejectsMismatchedTotal() {
        Offer o = coherentOffer();
        o.setTotalCtc(new BigDecimal("999999.00"));
        assertThatThrownBy(() -> policy.assertCompensationCoherent(o))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Total CTC");
    }

    @Test
    void compensationRequiresBaseSalary() {
        Offer o = new Offer();
        o.setTotalCtc(BigDecimal.TEN);
        assertThatThrownBy(() -> policy.assertCompensationCoherent(o))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Base salary");
    }

    @Test
    void draftIsEditable() {
        Offer o = statusOffer(OfferStatus.DRAFT);
        assertThatCode(() -> policy.assertEditable(o)).doesNotThrowAnyException();
    }

    @Test
    void approvedIsNotEditable() {
        Offer o = statusOffer(OfferStatus.APPROVED);
        assertThatThrownBy(() -> policy.assertEditable(o)).isInstanceOf(ApiException.class);
    }

    @Test
    void submittableRequiresDraftAndCoherentCompensation() {
        Offer o = statusOffer(OfferStatus.APPROVED);
        assertThatThrownBy(() -> policy.assertSubmittable(o)).isInstanceOf(ApiException.class);
        Offer d = coherentOffer();
        d.setStatus(OfferStatus.DRAFT);
        assertThatCode(() -> policy.assertSubmittable(d)).doesNotThrowAnyException();
    }

    @Test
    void extendableRequiresApproved() {
        assertThatThrownBy(() -> policy.assertExtendable(statusOffer(OfferStatus.DRAFT)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertExtendable(statusOffer(OfferStatus.APPROVED)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptRequiresExtendedAndNonExpired() {
        Offer o = statusOffer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.parse("2026-08-01T00:00:00Z").plusSeconds(3600));
        // Fake clock: current time earlier than expiry
        OfferPolicy p =
                new OfferPolicy(Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC));
        assertThatCode(() -> p.assertAcceptable(o)).doesNotThrowAnyException();

        o.setExpiresAt(Instant.parse("2025-01-01T00:00:00Z"));
        assertThatThrownBy(() -> p.assertAcceptable(o))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void withdrawableBlockedFromTerminal() {
        for (OfferStatus terminal :
                new OfferStatus[] {
                    OfferStatus.ACCEPTED,
                    OfferStatus.DECLINED,
                    OfferStatus.REJECTED,
                    OfferStatus.EXPIRED,
                    OfferStatus.WITHDRAWN,
                    OfferStatus.REVISED
                }) {
            assertThatThrownBy(() -> policy.assertWithdrawable(statusOffer(terminal)))
                    .isInstanceOf(ApiException.class);
        }
        assertThatCode(() -> policy.assertWithdrawable(statusOffer(OfferStatus.EXTENDED)))
                .doesNotThrowAnyException();
    }

    @Test
    void revisableAllowsExtendedAndPending() {
        assertThatCode(() -> policy.assertRevisable(statusOffer(OfferStatus.EXTENDED)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertRevisable(statusOffer(OfferStatus.PENDING_APPROVAL)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.assertRevisable(statusOffer(OfferStatus.ACCEPTED)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void isTerminalMatchesEnumSet() {
        assert policy.isTerminal(OfferStatus.ACCEPTED);
        assert policy.isTerminal(OfferStatus.WITHDRAWN);
        assert !policy.isTerminal(OfferStatus.APPROVED);
    }

    @Test
    void isExpiredRequiresExtendedAndPastExpiry() {
        OfferPolicy p =
                new OfferPolicy(Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC));
        Offer o = statusOffer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.parse("2026-08-01T00:00:00Z"));
        assert p.isExpired(o);

        o.setExpiresAt(Instant.parse("2027-01-01T00:00:00Z"));
        assert !p.isExpired(o);
    }

    private static Offer coherentOffer() {
        Offer o = new Offer();
        o.setStatus(OfferStatus.DRAFT);
        o.setCurrency("INR");
        o.setBaseSalary(new BigDecimal("1000000.00"));
        o.setVariablePay(new BigDecimal("200000.00"));
        o.setOneTimeBonus(new BigDecimal("50000.00"));
        o.setTotalCtc(new BigDecimal("1250000.00"));
        return o;
    }

    private static Offer statusOffer(OfferStatus status) {
        Offer o = new Offer();
        o.setStatus(status);
        return o;
    }
}
