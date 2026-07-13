package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** Small helpers for validating effective-date windows. */
final class EffectiveDateValidator {

    private EffectiveDateValidator() {}

    /** Rejects a window where {@code to} predates {@code from}. */
    static void requireOrdered(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "effectiveFrom is required");
        }
        if (to != null && to.isBefore(from)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "effectiveTo (" + to + ") must be on or after effectiveFrom (" + from + ")");
        }
    }
}
