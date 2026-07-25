package com.ewos.dataexchange.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkFailedRequest(
        @NotBlank @Size(max = 64) String errorCode,
        @NotBlank @Size(max = 2048) String errorMessage) {}
