package com.ewos.person.api.dto;

import com.ewos.person.domain.BloodGroup;
import com.ewos.person.domain.Gender;
import com.ewos.person.domain.MaritalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Historical or current Person profile snapshot")
public record PersonVersionResponse(
        UUID id,
        int versionNumber,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String firstName,
        String middleName,
        String lastName,
        String preferredName,
        Gender gender,
        LocalDate dateOfBirth,
        MaritalStatus maritalStatus,
        BloodGroup bloodGroup,
        String nationality,
        String photoUrl,
        String changeReason,
        UUID approvedBy,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
