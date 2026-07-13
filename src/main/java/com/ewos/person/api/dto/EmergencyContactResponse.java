package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Emergency contact for a Person")
public record EmergencyContactResponse(
        UUID id,
        UUID personId,
        String name,
        String relationship,
        int priority,
        String mobile,
        String alternateMobile,
        String email,
        String address,
        long version) {}
