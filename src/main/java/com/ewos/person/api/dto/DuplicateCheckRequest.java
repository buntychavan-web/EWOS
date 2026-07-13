package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Fields to check for duplicates before creating a Person")
public record DuplicateCheckRequest(
        @Schema(description = "Optional tenant id; defaults to DEFAULT") UUID tenantId,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        LocalDate dateOfBirth,
        @Size(max = 20) String mobile,
        @Size(max = 255) String email,
        @Size(max = 60) String pan,
        @Size(max = 60) String aadhaar,
        @Size(max = 60) String passport) {}
