package com.ewos.security.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot outside {@code dev} / {@code test} if {@code JWT_SECRET} looks like a placeholder
 * or fails to meet the HS256 minimum length (256 bits ≈ 32 bytes).
 *
 * <p>Placeholders come in two flavours we've shipped previously:
 *
 * <ul>
 *   <li>The default written into {@code application.yml} (starts with {@code change-me-please}).
 *   <li>The integration-test key in {@code application-test.yml} (contains {@code
 *       do-not-use-in-production}).
 * </ul>
 *
 * <p>Both are safe in {@code dev} / {@code test}; both are refused in every other profile.
 */
@Component
public class JwtSecretGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretGuard.class);

    /** Case-insensitive tokens that mark a secret as "not production-grade". */
    private static final List<String> PLACEHOLDER_MARKERS =
            List.of(
                    "change-me",
                    "changeme",
                    "do-not-use",
                    "donotuse",
                    "placeholder",
                    "example",
                    "sample");

    /** Profiles where a placeholder secret is tolerated with a warning. */
    private static final List<String> DEV_TEST_PROFILES = List.of("dev", "test", "default");

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final Environment environment;

    public JwtSecretGuard(JwtProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String secret = properties.secret();
        boolean devOrTest = isDevOrTestProfile();
        boolean placeholder = looksLikePlaceholder(secret);
        boolean tooShort =
                secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES;

        if (!devOrTest && (placeholder || tooShort)) {
            throw new IllegalStateException(
                    "JWT secret is not production-grade — refusing to start."
                            + " Set JWT_SECRET to a random value of at least "
                            + MIN_SECRET_BYTES
                            + " bytes."
                            + (placeholder ? " Current value looks like a placeholder." : "")
                            + (tooShort ? " Current value is too short for HS256." : ""));
        }
        if (devOrTest && placeholder) {
            log.warn(
                    "JWT_SECRET is a placeholder — acceptable only in dev/test profiles. "
                            + "Set JWT_SECRET explicitly before deploying anywhere else.");
        }
    }

    private boolean isDevOrTestProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            // Spring Boot's "default" profile is active when nothing is set — treat as dev.
            return true;
        }
        return Arrays.stream(active)
                .anyMatch(p -> DEV_TEST_PROFILES.contains(p.toLowerCase(Locale.ROOT)));
    }

    private static boolean looksLikePlaceholder(String secret) {
        if (secret == null) {
            return true;
        }
        String lower = secret.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains);
    }
}
