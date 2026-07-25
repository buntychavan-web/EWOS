package com.ewos.tenancy.domain;

/** What a provider-staff user is authorized to do on an assigned client. */
public enum ClientAssignmentScopeRole {
    PAYROLL_PROCESSOR,
    PAYROLL_REVIEWER,
    PAYROLL_ADMIN,
    AUDITOR_READ_ONLY
}
