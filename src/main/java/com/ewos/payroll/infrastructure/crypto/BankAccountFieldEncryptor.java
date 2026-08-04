package com.ewos.payroll.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM {@link AttributeConverter} protecting bank account numbers and routing codes at rest
 * (Codex CTO audit P0-2 — these columns were previously stored as plain text with encryption left
 * as "the operator's responsibility"; see the prior revision of {@code EmployeeBankAccount}'s class
 * javadoc).
 *
 * <p>The key material comes directly from the {@code BANK_ACCOUNT_ENCRYPTION_KEY} environment
 * variable (SHA-256-hashed down to a 256-bit AES key, so any-length passphrase is accepted — the
 * same "raw shared secret" convention this codebase already uses for {@code JWT_SECRET}), not
 * through Spring property injection: JPA attribute converters are instantiated by Hibernate's own
 * bean/converter registry, and reading the environment directly here removes any dependency on
 * whether that registry is Spring-aware. {@link BankAccountEncryptionKeyGuard} independently
 * verifies at boot that this same variable is not left as a placeholder outside dev/test, exactly
 * as {@code JwtSecretGuard} does for {@code JWT_SECRET}.
 *
 * <p>Each encryption uses a fresh random 96-bit IV; the stored value is {@code Base64(iv ||
 * ciphertext+tag)}. Two rows holding the same plaintext therefore never produce the same
 * ciphertext, and GCM's authentication tag detects tampering rather than silently mis-decrypting.
 */
@Converter
public class BankAccountFieldEncryptor implements AttributeConverter<String, String> {

    static final String ENV_VAR = "BANK_ACCOUNT_ENCRYPTION_KEY";
    static final String DEFAULT_KEY_MATERIAL =
            "change-me-please-use-a-256-bit-secret-in-production-environments";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final SecretKeySpec KEY = deriveKey(keyMaterial());

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt bank account field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt bank account field", e);
        }
    }

    static String keyMaterial() {
        String fromEnv = System.getenv(ENV_VAR);
        return (fromEnv == null || fromEnv.isBlank()) ? DEFAULT_KEY_MATERIAL : fromEnv;
    }

    // PMD.HardCodedCryptoKey pattern-matches the SecretKeySpec constructor call itself; the key
    // bytes here are a SHA-256 digest of runtime-supplied material (BANK_ACCOUNT_ENCRYPTION_KEY),
    // not a literal.
    @SuppressWarnings("PMD.HardCodedCryptoKey")
    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(sha256.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
