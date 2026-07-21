package com.ewos.ats.domain;

/**
 * Reduces a phone number to its digit-only form for duplicate-detection comparison. Never used for
 * display — the human-entered form is retained separately on {@link Candidate#getPhone()}.
 */
public final class PhoneNormalizer {

    private PhoneNormalizer() {}

    /** {@code null} in → {@code null} out; blank in → {@code null} out; otherwise digits only. */
    public static String digitsOnly(String phone) {
        if (phone == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(phone.length());
        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
