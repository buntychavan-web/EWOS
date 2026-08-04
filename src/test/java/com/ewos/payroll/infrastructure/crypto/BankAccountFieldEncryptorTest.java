package com.ewos.payroll.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class BankAccountFieldEncryptorTest {

    private final BankAccountFieldEncryptor converter = new BankAccountFieldEncryptor();

    @Test
    void roundTripsThePlaintext() {
        String plaintext = "1234567890123456";
        String stored = converter.convertToDatabaseColumn(plaintext);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(plaintext);
    }

    @Test
    void storedValueIsNotThePlaintext() {
        String plaintext = "1234567890123456";
        String stored = converter.convertToDatabaseColumn(plaintext);
        assertThat(stored).doesNotContain(plaintext);
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        String plaintext = "1234567890123456";
        String first = converter.convertToDatabaseColumn(plaintext);
        String second = converter.convertToDatabaseColumn(plaintext);
        assertThat(first).isNotEqualTo(second);
        assertThat(converter.convertToEntityAttribute(first)).isEqualTo(plaintext);
        assertThat(converter.convertToEntityAttribute(second)).isEqualTo(plaintext);
    }

    @Test
    void nullAndEmptyPassThroughUnchanged() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void tamperedCiphertextFailsToDecrypt() {
        String stored = converter.convertToDatabaseColumn("1234567890123456");
        byte[] bytes = Base64.getDecoder().decode(stored);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
