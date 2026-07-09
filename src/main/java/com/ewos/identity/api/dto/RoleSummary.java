package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Compact role reference embedded in user responses.")
public record RoleSummary(UUID id, String name) {}
