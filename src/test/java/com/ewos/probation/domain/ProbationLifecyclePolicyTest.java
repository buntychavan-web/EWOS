package com.ewos.probation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ProbationLifecyclePolicyTest {

    private final Clock fixed = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private final ProbationLifecyclePolicy policy = new ProbationLifecyclePolicy(fixed);

    @Test
    void assertOpenable_rejectsInvertedPeriod() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> policy.assertOpenable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("strictly after");
    }

    @Test
    void assertOpenable_acceptsValidPeriod() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 9, 1));
        assertThatCode(() -> policy.assertOpenable(r)).doesNotThrowAnyException();
    }

    @Test
    void assertExtendable_rejectsFromTerminalStatus() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.CONFIRMED);
        assertThatThrownBy(() -> policy.assertExtendable(r, LocalDate.of(2026, 8, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot extend");
    }

    @Test
    void assertExtendable_rejectsWhenNewEndNotAfterCurrent() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        assertThatThrownBy(() -> policy.assertExtendable(r, LocalDate.of(2026, 7, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("after current effective end");
    }

    @Test
    void assertExtendable_enforcesPolicyMaxExtensionDays() {
        ProbationPolicy pp = new ProbationPolicy();
        pp.setDefaultPeriodDays(90);
        pp.setMaxExtensionDays(30);
        ProbationRecord r = newRecord(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        r.setPolicy(pp);
        assertThatThrownBy(() -> policy.assertExtendable(r, LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("max_extension_days");
    }

    @Test
    void assertExtendable_allowsWithinPolicyLimit() {
        ProbationPolicy pp = new ProbationPolicy();
        pp.setDefaultPeriodDays(90);
        pp.setMaxExtensionDays(60);
        ProbationRecord r = newRecord(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 7, 1));
        r.setStatus(ProbationStatus.EXTENDED);
        r.setPolicy(pp);
        assertThatCode(() -> policy.assertExtendable(r, LocalDate.of(2026, 8, 15)))
                .doesNotThrowAnyException();
    }

    @Test
    void assertConfirmable_blocksEarlyWithoutPolicyPermission() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        assertThatThrownBy(() -> policy.assertConfirmable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("early confirmation");
    }

    @Test
    void assertConfirmable_allowsEarlyWhenPolicyPermits() {
        ProbationPolicy pp = new ProbationPolicy();
        pp.setAllowEarlyConfirm(true);
        ProbationRecord r = newRecord(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        r.setPolicy(pp);
        assertThatCode(() -> policy.assertConfirmable(r)).doesNotThrowAnyException();
    }

    @Test
    void assertConfirmable_allowsOnOrAfterEndDate() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1));
        r.setStatus(ProbationStatus.IN_PROBATION);
        assertThatCode(() -> policy.assertConfirmable(r)).doesNotThrowAnyException();
    }

    @Test
    void assertConfirmable_rejectsTerminalStatus() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1));
        r.setStatus(ProbationStatus.TERMINATED);
        assertThatThrownBy(() -> policy.assertConfirmable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void isTerminal_flagsExpectedStatuses() {
        assertThat(policy.isTerminal(ProbationStatus.CONFIRMED)).isTrue();
        assertThat(policy.isTerminal(ProbationStatus.TERMINATED)).isTrue();
        assertThat(policy.isTerminal(ProbationStatus.CANCELLED)).isTrue();
        assertThat(policy.isTerminal(ProbationStatus.IN_PROBATION)).isFalse();
        assertThat(policy.isTerminal(ProbationStatus.EXTENDED)).isFalse();
        assertThat(policy.isTerminal(ProbationStatus.PENDING_APPROVAL)).isFalse();
    }

    @Test
    void effectiveEnd_prefersExtendedEnd() {
        ProbationRecord r = newRecord(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1));
        assertThat(r.effectiveEnd()).isEqualTo(LocalDate.of(2026, 6, 1));
        r.setExtendedEnd(LocalDate.of(2026, 7, 15));
        assertThat(r.effectiveEnd()).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    private ProbationRecord newRecord(LocalDate start, LocalDate end) {
        ProbationRecord r = new ProbationRecord();
        r.setPeriodStart(start);
        r.setPeriodEnd(end);
        return r;
    }
}
