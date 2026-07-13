package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/** Effective-date range validation local to the person module. */
final class EffectiveDateValidator {

    private EffectiveDateValidator() {}

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
