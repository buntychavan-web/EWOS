package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Retire a dated child record by setting its effectiveTo")
public record RetireRequest(@NotNull LocalDate effectiveTo) {}
