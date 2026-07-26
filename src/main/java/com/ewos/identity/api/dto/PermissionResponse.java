package com.ewos.identity.api.dto;

import java.util.UUID;

public record PermissionResponse(UUID id, String code, String description) {}
