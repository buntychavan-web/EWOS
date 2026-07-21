package com.ewos.ats.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Lifecycle guard for {@link JobApplication}. Encodes the allowed forward transitions between
 * {@link ApplicationStatus} values; branch transitions (ON_HOLD / REJECTED / WITHDRAWN) have their
 * own guards.
 */
@Component
public class ApplicationPolicy {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> FORWARD =
            Map.ofEntries(
                    Map.entry(ApplicationStatus.NEW, EnumSet.of(ApplicationStatus.SCREENING)),
                    Map.entry(
                            ApplicationStatus.SCREENING, EnumSet.of(ApplicationStatus.SHORTLISTED)),
                    Map.entry(
                            ApplicationStatus.SHORTLISTED,
                            EnumSet.of(ApplicationStatus.INTERVIEW_SCHEDULED)),
                    Map.entry(
                            ApplicationStatus.INTERVIEW_SCHEDULED,
                            EnumSet.of(ApplicationStatus.INTERVIEWING)),
                    Map.entry(
                            ApplicationStatus.INTERVIEWING,
                            EnumSet.of(ApplicationStatus.INTERVIEW_COMPLETED)),
                    Map.entry(
                            ApplicationStatus.INTERVIEW_COMPLETED,
                            EnumSet.of(ApplicationStatus.OFFER_INITIATED)),
                    Map.entry(
                            ApplicationStatus.OFFER_INITIATED,
                            EnumSet.of(ApplicationStatus.OFFER_EXTENDED)),
                    Map.entry(
                            ApplicationStatus.OFFER_EXTENDED,
                            EnumSet.of(
                                    ApplicationStatus.OFFER_ACCEPTED,
                                    ApplicationStatus.OFFER_DECLINED)),
                    Map.entry(
                            ApplicationStatus.OFFER_ACCEPTED,
                            EnumSet.of(ApplicationStatus.ONBOARDING)),
                    Map.entry(ApplicationStatus.ONBOARDING, EnumSet.of(ApplicationStatus.HIRED)));

    /** Terminal states — no further transitions permitted from here. */
    private static final Set<ApplicationStatus> TERMINAL =
            EnumSet.of(
                    ApplicationStatus.HIRED,
                    ApplicationStatus.OFFER_DECLINED,
                    ApplicationStatus.REJECTED,
                    ApplicationStatus.WITHDRAWN);

    /** From which states can we branch off to ON_HOLD? Anything non-terminal that isn't NEW. */
    private static final Set<ApplicationStatus> HOLDABLE =
            EnumSet.of(
                    ApplicationStatus.SCREENING,
                    ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.INTERVIEW_SCHEDULED,
                    ApplicationStatus.INTERVIEWING,
                    ApplicationStatus.INTERVIEW_COMPLETED,
                    ApplicationStatus.OFFER_INITIATED,
                    ApplicationStatus.OFFER_EXTENDED);

    public void assertForwardTransition(JobApplication a, ApplicationStatus target) {
        Set<ApplicationStatus> allowed = FORWARD.get(a.getStatus());
        if (allowed == null || !allowed.contains(target)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot transition application from " + a.getStatus() + " to " + target);
        }
    }

    public void assertRejectable(JobApplication a) {
        if (TERMINAL.contains(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Application is already terminal (current: " + a.getStatus() + ")");
        }
    }

    public void assertWithdrawable(JobApplication a) {
        if (TERMINAL.contains(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Application is already terminal (current: " + a.getStatus() + ")");
        }
    }

    public void assertHoldable(JobApplication a) {
        if (!HOLDABLE.contains(a.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Application cannot be put on hold from " + a.getStatus());
        }
    }

    public void assertResumable(JobApplication a) {
        if (a.getStatus() != ApplicationStatus.ON_HOLD) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Application must be ON_HOLD to resume (current: " + a.getStatus() + ")");
        }
    }

    public boolean isTerminal(ApplicationStatus status) {
        return TERMINAL.contains(status);
    }
}
