package com.ewos.payroll.api.dto;

import java.util.UUID;

public record StatutoryJurisdictionResponse(
        UUID id, String countryCode, String stateCode, String name, boolean active) {}
