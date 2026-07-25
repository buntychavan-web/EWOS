package com.ewos.tenancy.domain;

/** Lifecycle status shared by Tenant, Client, Company, and Payroll Service Provider. */
public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
