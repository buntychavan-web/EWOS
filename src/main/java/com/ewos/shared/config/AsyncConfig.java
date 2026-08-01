package com.ewos.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} + {@code @TransactionalEventListener(AFTER_COMMIT)} publishing across the
 * platform. The pool is intentionally small: publishers should be fast (put-on-topic), and if
 * downstream is slow we prefer bounded queueing over unbounded thread growth.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("eventPublisherExecutor")
    public Executor eventPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1024);
        executor.setThreadNamePrefix("ewos-events-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Sprint 24B — long-running bulk operations (today: appraisal-cycle bulk launch), one job at a
     * time per request but potentially minutes long against 100k+ employees. Deliberately a
     * separate, tiny pool from {@code eventPublisherExecutor}: a slow bulk job must never starve
     * the fast, latency-sensitive event-publishing pool, and vice versa. {@code CallerRunsPolicy}
     * means a burst of launch requests beyond the queue backs the caller's HTTP thread up rather
     * than silently dropping a launch — acceptable here since launches are rare, admin-initiated,
     * already queued at most one-per-cycle by the DB (see {@code ux_launch_batches_cycle_active}).
     */
    @Bean("bulkOperationsExecutor")
    public Executor bulkOperationsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("ewos-bulk-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
