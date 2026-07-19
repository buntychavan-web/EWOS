package com.ewos.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One field-level validation error inside {@link ApiError#fieldErrors()}. Kept as a small,
 * consistent shape so the frontend can map errors 1:1 to form fields without parsing free text.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One field-level validation error")
public record FieldViolation(
        @Schema(description = "Dot-separated field path") String field,
        @Schema(description = "Rejected value; omitted when null or sensitive")
                Object rejectedValue,
        @Schema(description = "Human-readable reason") String message,
        @Schema(description = "Machine code, e.g. NotBlank / Size / Email") String code) {}
