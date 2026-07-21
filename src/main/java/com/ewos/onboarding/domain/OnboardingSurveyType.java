package com.ewos.onboarding.domain;

/**
 * Cadence of an onboarding survey. Standard cadence is 30 / 60 / 90 days; DAY_1 and WEEK_1 are
 * optional early check-ins; EXIT_ONBOARDING is the wrap-up before the plan closes.
 */
public enum OnboardingSurveyType {
    DAY_1,
    WEEK_1,
    DAY_30,
    DAY_60,
    DAY_90,
    EXIT_ONBOARDING
}
