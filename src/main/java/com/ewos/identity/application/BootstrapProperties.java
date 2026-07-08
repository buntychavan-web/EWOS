package com.ewos.identity.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.bootstrap.admin")
public record BootstrapProperties(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
