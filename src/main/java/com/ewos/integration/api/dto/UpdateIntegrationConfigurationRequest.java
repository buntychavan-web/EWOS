package com.ewos.integration.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateIntegrationConfigurationRequest(
        @Size(max = 4000) String configJson, Boolean active) {}
