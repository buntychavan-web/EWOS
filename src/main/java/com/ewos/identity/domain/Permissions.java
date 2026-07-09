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

    private Permissions() {}
}
