package com.ewos.ats.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class ApplicationPolicyTest {

    private final ApplicationPolicy policy = new ApplicationPolicy();

    @Test
    void newAdvancesToScreening() {
        JobApplication a = app(ApplicationStatus.NEW);
        assertThatCode(() -> policy.assertForwardTransition(a, ApplicationStatus.SCREENING))
                .doesNotThrowAnyException();
    }

    @Test
    void newCannotSkipToInterview() {
        JobApplication a = app(ApplicationStatus.NEW);
        assertThatThrownBy(() -> policy.assertForwardTransition(a, ApplicationStatus.INTERVIEWING))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void offerExtendedForksAcceptOrDecline() {
        JobApplication a = app(ApplicationStatus.OFFER_EXTENDED);
        assertThatCode(() -> policy.assertForwardTransition(a, ApplicationStatus.OFFER_ACCEPTED))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertForwardTransition(a, ApplicationStatus.OFFER_DECLINED))
                .doesNotThrowAnyException();
    }

    @Test
    void onboardingLeadsToHired() {
        JobApplication a = app(ApplicationStatus.ONBOARDING);
        assertThatCode(() -> policy.assertForwardTransition(a, ApplicationStatus.HIRED))
                .doesNotThrowAnyException();
    }

    @Test
    void terminalStatesCannotAdvance() {
        for (ApplicationStatus terminal :
                new ApplicationStatus[] {
                    ApplicationStatus.HIRED,
                    ApplicationStatus.REJECTED,
                    ApplicationStatus.WITHDRAWN,
                    ApplicationStatus.OFFER_DECLINED
                }) {
            JobApplication a = app(terminal);
            assertThatThrownBy(() -> policy.assertForwardTransition(a, ApplicationStatus.SCREENING))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void rejectAllowedFromNonTerminal() {
        JobApplication a = app(ApplicationStatus.INTERVIEWING);
        assertThatCode(() -> policy.assertRejectable(a)).doesNotThrowAnyException();
    }

    @Test
    void rejectBlockedFromTerminal() {
        JobApplication a = app(ApplicationStatus.HIRED);
        assertThatThrownBy(() -> policy.assertRejectable(a)).isInstanceOf(ApiException.class);
    }

    @Test
    void holdRequiresPipelineStage() {
        assertThatThrownBy(() -> policy.assertHoldable(app(ApplicationStatus.NEW)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertHoldable(app(ApplicationStatus.INTERVIEWING)))
                .doesNotThrowAnyException();
    }

    @Test
    void resumeRequiresOnHold() {
        assertThatThrownBy(() -> policy.assertResumable(app(ApplicationStatus.INTERVIEWING)))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> policy.assertResumable(app(ApplicationStatus.ON_HOLD)))
                .doesNotThrowAnyException();
    }

    @Test
    void terminalCheck() {
        assertThatCode(() -> policy.isTerminal(ApplicationStatus.HIRED)).doesNotThrowAnyException();
        assert policy.isTerminal(ApplicationStatus.REJECTED);
        assert !policy.isTerminal(ApplicationStatus.OFFER_EXTENDED);
    }

    private static JobApplication app(ApplicationStatus status) {
        JobApplication a = new JobApplication();
        a.setStatus(status);
        return a;
    }
}
