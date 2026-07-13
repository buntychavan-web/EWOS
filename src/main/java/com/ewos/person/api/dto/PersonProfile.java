package com.ewos.person.api.dto;

import com.ewos.person.domain.BloodGroup;
import com.ewos.person.domain.Gender;
import com.ewos.person.domain.MaritalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Effective-dated core profile for a Person")
public record PersonProfile(
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String middleName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String preferredName,
        Gender gender,
        LocalDate dateOfBirth,
        MaritalStatus maritalStatus,
        BloodGroup bloodGroup,
        @Size(max = 100) String nationality,
        @Size(max = 500) String photoUrl,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String changeReason) {}
