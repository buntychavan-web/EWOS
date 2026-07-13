package com.ewos.person.api.dto;

import com.ewos.person.domain.FamilyRelation;
import com.ewos.person.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Family member of a Person")
public record FamilyMemberResponse(
        UUID id,
        UUID personId,
        FamilyRelation relation,
        String name,
        LocalDate dateOfBirth,
        Gender gender,
        String occupation,
        boolean dependent,
        String mobile,
        String email,
        long version) {}
