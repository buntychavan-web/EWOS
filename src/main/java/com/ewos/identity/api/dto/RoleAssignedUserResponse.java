package com.ewos.identity.api.dto;

import java.util.UUID;

public record RoleAssignedUserResponse(UUID id, String username, String email, boolean enabled) {}
