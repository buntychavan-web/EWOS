package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Add an education record")
public record AddEducationRequest(
        @NotBlank @Size(max = 255) String qualification,
        @NotBlank @Size(max = 255) String institution,
        @NotNull @Min(1900) @Max(2200) Integer passingYear,
        @Size(max = 60) String grade,
        @Size(max = 255) String specialization,
        @Size(max = 500) String documentUrl) {}
