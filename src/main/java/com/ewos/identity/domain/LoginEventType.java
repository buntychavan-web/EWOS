package com.ewos.identity.domain;

/**
 * Authentication lifecycle events written to {@code login_history}. Success is derived from the
 * event: success/logout events carry {@code success = true}.
 */
public enum LoginEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    REFRESH_SUCCESS,
    REFRESH_FAILURE
}
