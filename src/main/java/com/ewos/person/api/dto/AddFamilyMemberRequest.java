package com.ewos.person.api.dto;

import com.ewos.person.domain.FamilyRelation;
import com.ewos.person.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Add a family member")
public record AddFamilyMemberRequest(
        @NotNull FamilyRelation relation,
        @NotBlank @Size(max = 255) String name,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 255) String occupation,
        boolean dependent,
        @Size(max = 20) String mobile,
        @Email @Size(max = 255) String email) {}
