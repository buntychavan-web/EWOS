package com.ewos.shared.config;

import java.util.concurrent.Executor;
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
}
