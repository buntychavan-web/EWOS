package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator(PasswordPolicyProperties policy) {
        return new PasswordPolicyValidator(policy);
    }

    private static PasswordPolicyProperties strict() {
        return new PasswordPolicyProperties(8, 128, true, true, true, true, 5);
    }

    @Test
    void acceptsCompliantPassword() {
        assertThatCode(() -> validator(strict()).validate("V@lid1Passw0rd"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> validator(strict()).validate("Ab1!"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("minimum length");
    }

    @Test
    void rejectsMissingUppercase() {
        assertThatThrownBy(() -> validator(strict()).validate("nouppers1!"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void rejectsMissingDigit() {
        assertThatThrownBy(() -> validator(strict()).validate("NoDigits!!"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("digit");
    }

    @Test
    void rejectsMissingSpecial() {
        assertThatThrownBy(() -> validator(strict()).validate("NoSpecial1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("special");
    }

    @Test
    void relaxedPolicyAllowsSimplerPasswords() {
        PasswordPolicyProperties relaxed =
                new PasswordPolicyProperties(6, 128, false, false, false, false, 0);
        assertThatCode(() -> validator(relaxed).validate("simple")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullPassword() {
        assertThatThrownBy(() -> validator(strict()).validate(null))
                .isInstanceOf(ApiException.class);
    }
}
