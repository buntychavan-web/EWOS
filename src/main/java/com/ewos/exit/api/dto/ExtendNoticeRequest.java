package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ExtendNoticeRequest(
        @NotNull LocalDate newNoticeEndDate, @NotBlank @Size(max = 2000) String reason) {}
