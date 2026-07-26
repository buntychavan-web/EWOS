package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.UUID;

@Schema(
        description =
                "Current authenticated identity snapshot: user, tenant (if resolved), and employee (if"
                        + " linked — see Sprint 1.3's Employee<->User link).")
public record MeResponse(
        UUID userId,
        String username,
        String email,
        Set<RoleSummary> roles,
        UUID tenantId,
        UUID employeeId) {}
