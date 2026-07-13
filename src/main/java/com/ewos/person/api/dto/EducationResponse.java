package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Education record for a Person")
public record EducationResponse(
        UUID id,
        UUID personId,
        String qualification,
        String institution,
        int passingYear,
        String grade,
        String specialization,
        String documentUrl,
        long version) {}
