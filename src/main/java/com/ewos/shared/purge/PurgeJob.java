package com.ewos.shared.purge;

import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled purge framework. Ships <strong>disabled by default</strong> so nothing is ever removed
 * until an operator turns each job on. The intent is to hold the shape of the framework so future
 * WPs (soft-deleted rows, audit-log trimming, etc.) can plug into the same schedule and reporting.
 *
 * <p>Current jobs:
 *
 * <ul>
 *   <li>Expired refresh tokens — removes revoked / expired rows older than {@code
 *       expiredRefreshTokenRetention}. Wired up but off by default.
 * </ul>
 *
 * <p>Future jobs (stubs): soft-deleted users / roles / permissions / persons past the retention
 * window. Adding a stub is a two-line change — see {@code purgeSoftDeletedRows} below.
 */
@Component
@EnableConfigurationProperties(PurgeProperties.class)
public class PurgeJob {

    private static final Logger log = LoggerFactory.getLogger(PurgeJob.class);

    private final PurgeProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;

    public PurgeJob(PurgeProperties properties, RefreshTokenRepository refreshTokenRepository) {
        this.properties = properties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Runs on the schedule defined by {@code app.purge.cron}. Individual sub-jobs additionally
     * check their own {@code *Enabled} flag so they can be turned on one at a time.
     */
    @Scheduled(cron = "${app.purge.cron:0 15 3 * * *}", zone = "${app.purge.zone:UTC}")
    @Transactional
    public void runAll() {
        if (!properties.enabled()) {
            return;
        }
        log.info("Purge sweep starting");
        purgeExpiredRefreshTokens();
        purgeSoftDeletedRows();
        log.info("Purge sweep finished");
    }

    void purgeExpiredRefreshTokens() {
        if (!properties.expiredRefreshTokensEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.expiredRefreshTokenRetention());
        int deleted = refreshTokenRepository.deleteAllExpired(cutoff);
        log.info("Purged {} expired refresh tokens older than {}", deleted, cutoff);
    }

    void purgeSoftDeletedRows() {
        if (!properties.softDeletedRowsEnabled()) {
            return;
        }
        // Stub — populated once each module owns its soft-delete retention policy.
        log.info(
                "Soft-deleted-row purge is enabled but no per-module purger is implemented yet."
                        + " Add module-specific queries here in a follow-up WP.");
    }

    // Provided so integration tests can trigger the run without waiting for cron.
    @Value("${app.purge.enabled:false}")
    private transient boolean legacyEnabledForDoc;

    /** Public trigger for tests / operator scripts. Bypasses the schedule but respects flags. */
    @Transactional
    public void runNow() {
        runAll();
    }
}
