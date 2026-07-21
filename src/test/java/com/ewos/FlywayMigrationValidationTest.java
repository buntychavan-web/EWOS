package com.ewos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Verifies the Flyway migrations are internally consistent + apply cleanly against a fresh
 * database. Extends {@link AbstractIntegrationTest} so it shares the singleton Testcontainers
 * Postgres.
 *
 * <p>Checks:
 *
 * <ul>
 *   <li>Every {@code V{N}__*.sql} file under {@code classpath:db/migration/} is picked up.
 *   <li>{@code migrate()} succeeds against an empty schema.
 *   <li>The highest version applied matches the highest {@code V{N}} in the classpath.
 *   <li>Version numbers are strictly monotonic with no gaps or duplicates.
 * </ul>
 */
class FlywayMigrationValidationTest extends AbstractIntegrationTest {

    private static final Pattern V_FILE = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Autowired Flyway flyway;

    @Test
    void migrationsAreDiscoverableSequentialAndApplyCleanly() throws Exception {
        int[] versions = collectVersionsFromClasspath();

        // Sequential + no duplicates.
        assertThat(versions).isSorted();
        assertThat(versions).doesNotHaveDuplicates();
        for (int i = 1; i < versions.length; i++) {
            assertThat(versions[i])
                    .as("no gaps between V%d and V%d", versions[i - 1], versions[i])
                    .isEqualTo(versions[i - 1] + 1);
        }

        // Fresh run against an isolated schema.
        Flyway fresh =
                Flyway.configure()
                        .dataSource(
                                new DriverManagerDataSource(
                                        POSTGRES.getJdbcUrl()
                                                + "&currentSchema=flyway_validation_test",
                                        POSTGRES.getUsername(),
                                        POSTGRES.getPassword()))
                        .schemas("flyway_validation_test")
                        .cleanDisabled(false)
                        .load();
        fresh.clean();
        MigrateResult result = fresh.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(versions.length);

        MigrationVersion highest =
                MigrationVersion.fromVersion(Integer.toString(versions[versions.length - 1]));
        assertThat(fresh.info().current().getVersion()).isEqualTo(highest);

        // Confirm the Spring-managed Flyway also converged to the same version.
        assertThat(flyway.info().current().getVersion()).isEqualTo(highest);
    }

    private static int[] collectVersionsFromClasspath() throws Exception {
        Resource[] resources =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath:db/migration/V*__*.sql");
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .map(V_FILE::matcher)
                .filter(Matcher::matches)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .sorted()
                .toArray();
    }
}
