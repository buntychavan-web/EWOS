package com.ewos.identity.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot outside {@code dev} / {@code test} if {@code ADMIN_PASSWORD} still holds the
 * placeholder shipped in {@code application.yml} ({@code ChangeMe!Admin123}) or another obvious
 * default.
 *
 * <p>Mirrors {@link com.ewos.identity.infrastructure.security.jwt.JwtSecretGuard}: the placeholder
 * is safe in {@code dev} / {@code test} (with a warning) and refused everywhere else, since {@link
 * IdentityBootstrap} would otherwise create a production System Administrator account with a
 * publicly-known password.
 */
@Component
public class AdminPasswordGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AdminPasswordGuard.class);

    private static final List<String> PLACEHOLDER_MARKERS =
            List.of(
                    "change-me",
                    "changeme",
                    "do-not-use",
                    "donotuse",
                    "placeholder",
                    "example",
                    "sample");

    private static final List<String> DEV_TEST_PROFILES = List.of("dev", "test", "default");

    private final BootstrapProperties properties;
    private final Environment environment;

    public AdminPasswordGuard(BootstrapProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String password = properties.password();
        boolean devOrTest = isDevOrTestProfile();
        boolean placeholder = looksLikePlaceholder(password);

        if (!devOrTest && placeholder) {
            throw new IllegalStateException(
                    "Bootstrap admin password is not production-grade — refusing to start."
                            + " Set ADMIN_PASSWORD to a strong, unique value before deploying"
                            + " outside dev/test.");
        }
        if (devOrTest && placeholder) {
            log.warn(
                    "ADMIN_PASSWORD is a placeholder — acceptable only in dev/test profiles. "
                            + "Set ADMIN_PASSWORD explicitly before deploying anywhere else.");
        }
    }

    private boolean isDevOrTestProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active)
                .anyMatch(p -> DEV_TEST_PROFILES.contains(p.toLowerCase(Locale.ROOT)));
    }

    private static boolean looksLikePlaceholder(String password) {
        if (password == null) {
            return true;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains);
    }
}
