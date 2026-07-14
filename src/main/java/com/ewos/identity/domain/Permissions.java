package com.ewos.identity.domain;

/**
 * Canonical permission codes. Kept as {@code static final String} constants so they can be
 * referenced from {@code @PreAuthorize} expressions.
 */
public final class Permissions {

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String USER_READ = "USER_READ";
    public static final String USER_WRITE = "USER_WRITE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String ROLE_READ = "ROLE_READ";
    public static final String ROLE_WRITE = "ROLE_WRITE";
    public static final String COMPANY_READ = "COMPANY_READ";
    public static final String COMPANY_WRITE = "COMPANY_WRITE";
    public static final String COMPANY_DELETE = "COMPANY_DELETE";
    public static final String ORGANIZATION_READ = "ORGANIZATION_READ";
    public static final String ORGANIZATION_WRITE = "ORGANIZATION_WRITE";
    public static final String ORGANIZATION_DELETE = "ORGANIZATION_DELETE";
    public static final String PERSON_READ = "PERSON_READ";
    public static final String PERSON_WRITE = "PERSON_WRITE";
    public static final String PERSON_DELETE = "PERSON_DELETE";
    public static final String PERSON_DUPLICATE_OVERRIDE = "PERSON_DUPLICATE_OVERRIDE";
    public static final String DASHBOARD_READ = "DASHBOARD_READ";

    private Permissions() {}
}
