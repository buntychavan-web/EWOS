package com.ewos;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 *
 * <p>The Postgres container is started once per JVM via a static initializer (the "singleton
 * container" pattern) and deliberately never stopped in test code — the Testcontainers Ryuk sidecar
 * terminates it when the JVM exits. This avoids the {@code @Container} + {@code @Testcontainers}
 * lifecycle bug where the second {@code @SpringBootTest} class in a run calls {@code start()} on an
 * already-stopped container, which is a silent no-op and leaves subsequent tests hitting a dead
 * port ("Connection refused") until Hikari times out.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("ewos_test")
                        .withUsername("ewos")
                        .withPassword("ewos");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
