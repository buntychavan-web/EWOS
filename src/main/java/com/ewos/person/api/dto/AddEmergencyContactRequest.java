package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Add an emergency contact")
public record AddEmergencyContactRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 60) String relationship,
        @NotNull @Min(1) Integer priority,
        @NotBlank @Size(max = 20) String mobile,
        @Size(max = 20) String alternateMobile,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String address) {}
