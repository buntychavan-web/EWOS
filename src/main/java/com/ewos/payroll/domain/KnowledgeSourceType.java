package com.ewos.payroll.domain;

/** What kind of authoritative source a {@link KnowledgeDocument} row records. */
public enum KnowledgeSourceType {
    INCOME_TAX_ACT,
    CBDT_CIRCULAR,
    EPFO_CIRCULAR,
    ESIC_CIRCULAR,
    PT_NOTIFICATION,
    LWF_NOTIFICATION,
    COMPANY_POLICY,
    OTHER
}
