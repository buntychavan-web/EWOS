package com.ewos.interview.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InterviewPolicyTest {

    private final InterviewPolicy policy = new InterviewPolicy();

    @Test
    void draftIsSchedulable() {
        InterviewRound r = round(InterviewStatus.DRAFT);
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        Instant end = start.plusSeconds(3600);
        assertThatCode(() -> policy.assertSchedulable(r, start, end)).doesNotThrowAnyException();
    }

    @Test
    void scheduleRejectsEndNotAfterStart() {
        InterviewRound r = round(InterviewStatus.DRAFT);
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        assertThatThrownBy(() -> policy.assertSchedulable(r, start, start))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("End must be strictly after start");
    }

    @Test
    void completedNotSchedulable() {
        InterviewRound r = round(InterviewStatus.COMPLETED);
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        Instant end = start.plusSeconds(3600);
        assertThatThrownBy(() -> policy.assertSchedulable(r, start, end))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void scheduledIsStartable() {
        InterviewRound r = round(InterviewStatus.SCHEDULED);
        assertThatCode(() -> policy.assertStartable(r)).doesNotThrowAnyException();
    }

    @Test
    void draftNotStartable() {
        InterviewRound r = round(InterviewStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertStartable(r)).isInstanceOf(ApiException.class);
    }

    @Test
    void inProgressIsCompletable() {
        InterviewRound r = round(InterviewStatus.IN_PROGRESS);
        assertThatCode(() -> policy.assertCompletable(r)).doesNotThrowAnyException();
    }

    @Test
    void completedIsNotCancellable() {
        InterviewRound r = round(InterviewStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertCancellable(r)).isInstanceOf(ApiException.class);
    }

    @Test
    void scheduledIsCancellable() {
        InterviewRound r = round(InterviewStatus.SCHEDULED);
        assertThatCode(() -> policy.assertCancellable(r)).doesNotThrowAnyException();
    }

    @Test
    void decisionRequiresCompletedOrPending() {
        assertThatThrownBy(() -> policy.assertDecidable(round(InterviewStatus.SCHEDULED)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertDecidable(round(InterviewStatus.COMPLETED)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertDecidable(round(InterviewStatus.PENDING_FEEDBACK)))
                .doesNotThrowAnyException();
    }

    @Test
    void scorecardBlockedFromDraftOrCancelled() {
        assertThatThrownBy(() -> policy.assertScorecardSubmittable(round(InterviewStatus.DRAFT)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(
                        () -> policy.assertScorecardSubmittable(round(InterviewStatus.CANCELLED)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertScorecardSubmittable(round(InterviewStatus.COMPLETED)))
                .doesNotThrowAnyException();
    }

    @Test
    void noShowRequiresScheduledOrInProgress() {
        assertThatThrownBy(() -> policy.assertNoShowable(round(InterviewStatus.DRAFT)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertNoShowable(round(InterviewStatus.SCHEDULED)))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertNoShowable(round(InterviewStatus.IN_PROGRESS)))
                .doesNotThrowAnyException();
    }

    private static InterviewRound round(InterviewStatus status) {
        InterviewRound r = new InterviewRound();
        r.setStatus(status);
        return r;
    }
}
