package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ClientAssignmentScopeRole;
import java.time.LocalDate;

public record UpdateClientAssignmentRequest(
        ClientAssignmentScopeRole scopeRole, Boolean active, LocalDate effectiveTo) {}
