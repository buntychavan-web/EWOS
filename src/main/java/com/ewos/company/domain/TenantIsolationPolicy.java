package com.ewos.company.domain;

/**
 * Data-isolation strategy for a tenant that owns multiple companies.
 *
 * <ul>
 *   <li>{@link #SHARED} — Shared Enterprise: data is visible across all companies within the tenant
 *       by default (employees can move between companies, single reporting hierarchy).
 *   <li>{@link #SEGREGATED} — Segregated Enterprise: each company is walled off; cross-company
 *       queries require an explicit administrator action.
 * </ul>
 *
 * <p>The current sprint stores the policy but does not yet enforce it at the query layer. The
 * enforcement mechanism (Hibernate filter driven by tenant context in the JWT) is Sprint 6.1.
 */
public enum TenantIsolationPolicy {
    SHARED,
    SEGREGATED
}
