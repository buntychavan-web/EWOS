package com.ewos.payroll.infrastructure.crypto;

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
 * Refuses to boot outside {@code dev} / {@code test} if {@code BANK_ACCOUNT_ENCRYPTION_KEY} looks
 * like a placeholder or is too short for a 256-bit AES key — the same fail-fast contract {@code
 * JwtSecretGuard} applies to {@code JWT_SECRET}, applied here for the key {@link
 * BankAccountFieldEncryptor} derives its AES key from.
 */
@Component
public class BankAccountEncryptionKeyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(BankAccountEncryptionKeyGuard.class);

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

    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;

    public BankAccountEncryptionKeyGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String key = BankAccountFieldEncryptor.keyMaterial();
        boolean devOrTest = isDevOrTestProfile();
        boolean placeholder = looksLikePlaceholder(key);
        boolean tooShort = key.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES;

        if (!devOrTest && (placeholder || tooShort)) {
            throw new IllegalStateException(
                    "BANK_ACCOUNT_ENCRYPTION_KEY is not production-grade — refusing to start."
                            + " Set BANK_ACCOUNT_ENCRYPTION_KEY to a random value of at least "
                            + MIN_SECRET_BYTES
                            + " bytes."
                            + (placeholder ? " Current value looks like a placeholder." : "")
                            + (tooShort ? " Current value is too short." : ""));
        }
        if (devOrTest && placeholder) {
            log.warn(
                    "BANK_ACCOUNT_ENCRYPTION_KEY is a placeholder — acceptable only in dev/test"
                            + " profiles. Set it explicitly before deploying anywhere else.");
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

    private static boolean looksLikePlaceholder(String secret) {
        String lower = secret.toLowerCase(Locale.ROOT);
        return PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains);
    }
}
