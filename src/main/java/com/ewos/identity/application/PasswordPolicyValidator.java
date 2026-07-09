package com.ewos.identity.application;

import com.ewos.common.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Enforces the configured {@link PasswordPolicyProperties}. Throws {@link ApiException} with 400
 * Bad Request when the candidate fails — the message lists every rule that was violated.
 */
@Service
public class PasswordPolicyValidator {

    private static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{};':\",.<>/?\\|`~";

    private final PasswordPolicyProperties policy;

    public PasswordPolicyValidator(PasswordPolicyProperties policy) {
        this.policy = policy;
    }

    public void validate(String password) {
        if (password == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        List<String> problems = new ArrayList<>();

        if (password.length() < policy.minLength()) {
            problems.add("minimum length " + policy.minLength());
        }
        if (password.length() > policy.maxLength()) {
            problems.add("maximum length " + policy.maxLength());
        }
        if (policy.requireUppercase() && !password.chars().anyMatch(Character::isUpperCase)) {
            problems.add("at least one uppercase letter");
        }
        if (policy.requireLowercase() && !password.chars().anyMatch(Character::isLowerCase)) {
            problems.add("at least one lowercase letter");
        }
        if (policy.requireDigit() && !password.chars().anyMatch(Character::isDigit)) {
            problems.add("at least one digit");
        }
        if (policy.requireSpecial() && !containsSpecial(password)) {
            problems.add("at least one special character");
        }

        if (!problems.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Password does not meet policy: " + String.join(", ", problems));
        }
    }

    private static boolean containsSpecial(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (SPECIAL_CHARS.indexOf(password.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
