package com.ewos.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Sprint 20 — regression guard for {@code application.yml}'s graceful-shutdown settings. A rolling
 * Kubernetes deploy sends SIGTERM; without {@code server.shutdown: graceful}, Tomcat's default
 * (IMMEDIATE) shutdown drops every in-flight request the instant that arrives — including a payroll
 * run. Binds the real property keys through Spring Boot's own {@link ServerProperties} / {@link
 * LifecycleProperties} classes rather than string-matching the YAML, so a typo'd key (which would
 * silently bind to nothing and revert to IMMEDIATE) fails this test instead of only failing in a
 * production incident.
 */
class GracefulShutdownConfigTest {

    @Test
    void serverShutdownIsGraceful() {
        assertThat(bind("server", ServerProperties.class).getShutdown())
                .isEqualTo(Shutdown.GRACEFUL);
    }

    @Test
    void shutdownPhaseTimeoutIsBounded() {
        assertThat(bind("spring.lifecycle", LifecycleProperties.class).getTimeoutPerShutdownPhase())
                .isEqualTo(Duration.ofSeconds(30));
    }

    private static <T> T bind(String prefix, Class<T> target) {
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            StandardEnvironment environment = new StandardEnvironment();
            for (PropertySource<?> source :
                    loader.load("application", new ClassPathResource("application.yml"))) {
                environment.getPropertySources().addLast(source);
            }
            return Binder.get(environment).bind(prefix, target).get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
