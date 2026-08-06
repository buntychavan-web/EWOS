package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceDepartment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateClearanceRequest(
        @NotNull ClearanceDepartment department,
        @Size(max = 200) String itemName,
        UUID ownerEmployeeId,
        String notes) {}
