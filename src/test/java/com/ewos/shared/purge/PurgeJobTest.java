package com.ewos.shared.purge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import com.ewos.shared.audit.CrossEmployeeAccessLogRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 27A — covers the new {@code purgeMssAccessLogs} sub-job added to the existing framework.
 */
@ExtendWith(MockitoExtension.class)
class PurgeJobTest {

    @Mock RefreshTokenRepository refreshTokens;
    @Mock CrossEmployeeAccessLogRepository mssAccessLogs;

    @Test
    void purgeMssAccessLogsDoesNothingWhenDisabled() {
        PurgeJob job = new PurgeJob(properties(false, false, false), refreshTokens, mssAccessLogs);

        job.purgeMssAccessLogs();

        verify(mssAccessLogs, never()).deleteAllOlderThan(any());
    }

    @Test
    void purgeMssAccessLogsDeletesRowsOlderThanTheConfiguredRetentionWhenEnabled() {
        PurgeJob job = new PurgeJob(properties(false, false, true), refreshTokens, mssAccessLogs);
        when(mssAccessLogs.deleteAllOlderThan(any())).thenReturn(3);

        job.purgeMssAccessLogs();

        verify(mssAccessLogs).deleteAllOlderThan(any());
    }

    @Test
    void runAllRespectsTheGlobalEnabledFlagEvenWithSubJobsOn() {
        PurgeJob job = new PurgeJob(properties(false, false, true), refreshTokens, mssAccessLogs);
        PurgeProperties globallyDisabled =
                new PurgeProperties(
                        false,
                        null,
                        Duration.ofDays(30),
                        Duration.ofDays(365),
                        Duration.ofDays(180),
                        false,
                        false,
                        true);
        job = new PurgeJob(globallyDisabled, refreshTokens, mssAccessLogs);

        job.runAll();

        verify(mssAccessLogs, never()).deleteAllOlderThan(any());
    }

    private static PurgeProperties properties(
            boolean refreshTokensEnabled,
            boolean softDeletedEnabled,
            boolean mssAccessLogsEnabled) {
        return new PurgeProperties(
                true,
                null,
                Duration.ofDays(30),
                Duration.ofDays(365),
                Duration.ofDays(180),
                refreshTokensEnabled,
                softDeletedEnabled,
                mssAccessLogsEnabled);
    }
}
