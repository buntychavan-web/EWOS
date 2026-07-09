package com.ewos.identity.application;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.password-policy")
public record PasswordPolicyProperties(
        @Min(6) int minLength,
        @Min(8) int maxLength,
        boolean requireUppercase,
        boolean requireLowercase,
        boolean requireDigit,
        boolean requireSpecial,
        @Min(0) int historySize
) {
}
