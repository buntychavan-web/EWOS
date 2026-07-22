package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceDepartment;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateClearanceRequest(
        @NotNull ClearanceDepartment department, UUID ownerEmployeeId, String notes) {}
