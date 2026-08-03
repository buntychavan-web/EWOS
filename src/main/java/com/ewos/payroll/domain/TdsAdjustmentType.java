package com.ewos.payroll.domain;

/** Why a period's actual TDS recovery differed from the plain even-share amount. */
public enum TdsAdjustmentType {
    /** Recovery was capped below the even share to avoid an unrealistic/negative net pay. */
    SHORTFALL_CAP,
    /** Extra tax recovered this period because of a one-time/variable payment. */
    VARIABLE_PAYMENT_INCREMENTAL
}
