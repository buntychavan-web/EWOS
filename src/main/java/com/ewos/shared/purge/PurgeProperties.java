package com.ewos.shared.purge;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the scheduled data-purge framework. Ships <strong>disabled by default</strong>
 * — nothing is deleted until an operator explicitly turns each job on. See {@link PurgeJob}.
 */
@ConfigurationProperties(prefix = "app.purge")
public record PurgeProperties(
        boolean enabled,
        String cron,
        Duration expiredRefreshTokenRetention,
        Duration softDeletedRowRetention,
        boolean expiredRefreshTokensEnabled,
        boolean softDeletedRowsEnabled) {

    public PurgeProperties {
        if (cron == null || cron.isBlank()) {
            // Default: 03:15 every day, low-traffic window.
            cron = "0 15 3 * * *";
        }
        if (expiredRefreshTokenRetention == null || expiredRefreshTokenRetention.isNegative()) {
            expiredRefreshTokenRetention = Duration.ofDays(30);
        }
        if (softDeletedRowRetention == null || softDeletedRowRetention.isNegative()) {
            softDeletedRowRetention = Duration.ofDays(365);
        }
    }
}
