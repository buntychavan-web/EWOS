package com.ewos.notification.domain;

/** What triggered a {@link Notification} — drives icon/routing on the frontend. */
public enum NotificationType {
    TASK_ASSIGNED,
    TASK_ESCALATED,
    INSTANCE_COMPLETED,
    INSTANCE_CANCELLED,
    INSTANCE_ERRORED,
    GENERIC,

    // Sprint 24B — Performance module. Each in-app row here is the IN_APP channel of an
    // otherwise channel-agnostic notification-worthy event; see
    // com.ewos.performance.application.PerformanceNotificationEventListener for the seam other
    // channels (email/SMS/push) would plug into.
    PERF_SELF_REVIEW_OPENED,
    PERF_MANAGER_REVIEW_PENDING,
    PERF_REVIEWER_REVIEW_PENDING,
    PERF_REVIEW_REMINDER,
    PERF_FINAL_RATING_RELEASED,
    PERF_BULK_LAUNCH_COMPLETED,

    // Sprint 24E — Calibration. See PerformanceNotificationEventListener; previously
    // CALIBRATION_SESSION_CREATED/COMPLETED were deliberately dropped (no single unambiguous
    // recipient among appraisal participants) — the calibration session's own facilitatorId is
    // the recipient here, not an appraisal participant.
    CALIBRATION_SESSION_OPENED,
    CALIBRATION_COMPLETED,

    // Sprint 24E — Goals. See com.ewos.goals.application.GoalNotificationEventListener.
    GOAL_ASSIGNED,
    GOAL_REVIEW_PENDING,
    GOAL_REVIEWED,
    GOAL_COMPLETED,
    GOAL_CANCELLED,
    GOAL_DUE_REMINDER,
    GOAL_OVERDUE,

    // Sprint 24E — Competency & Development Plans. See
    // com.ewos.competency.application.CompetencyNotificationEventListener.
    COMPETENCY_ASSESSED,
    DEVPLAN_ACTIVATED,
    DEVPLAN_COMPLETED,
    DEVPLAN_ACTION_DUE,
    DEVPLAN_ACTION_OVERDUE,

    // Sprint 24F — Recruitment. See
    // com.ewos.recruitment.application.RecruitmentNotificationEventListener.
    REQUISITION_APPROVED,
    REQUISITION_REJECTED,
    REQUISITION_FILLED,
    REQUISITION_CLOSED,

    // Sprint 24F — ATS. See com.ewos.ats.application.AtsNotificationEventListener.
    APPLICATION_OFFER_ACCEPTED,
    APPLICATION_OFFER_DECLINED,

    // Sprint 24F — Interview. See
    // com.ewos.interview.application.InterviewNotificationEventListener.
    INTERVIEW_ROUND_SCHEDULED,
    INTERVIEW_ROUND_RESCHEDULED,
    INTERVIEW_ROUND_CANCELLED,
    INTERVIEW_SCORECARD_SUBMITTED,
    INTERVIEW_ROUND_DECIDED,

    // Sprint 24F — Offer. See com.ewos.offer.application.OfferNotificationEventListener.
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    OFFER_EXPIRED,

    // Sprint 24F — Onboarding. See
    // com.ewos.onboarding.application.OnboardingNotificationEventListener.
    ONBOARDING_TASK_ASSIGNED,
    ONBOARDING_TASK_REASSIGNED,
    ONBOARDING_TASK_OVERDUE,
    ONBOARDING_PLAN_COMPLETED,

    // Sprint 24F — Payroll. See com.ewos.payroll.application.PayrollNotificationEventListener.
    PAYSLIP_READY,
    PAYROLL_RUN_FINALIZED,
    PAYROLL_RUN_FAILED,

    // Sprint 24F — Identity. See
    // com.ewos.identity.application.IdentityNotificationEventListener.
    PASSWORD_RESET_BY_ADMIN,
    ACCOUNT_LOCKED,
    ACCOUNT_DISABLED,

    // Sprint 24F — Tenancy. See
    // com.ewos.tenancy.application.TenancyNotificationEventListener.
    TENANT_ACCESS_GRANTED,
    TENANT_ACCESS_REVOKED
}
