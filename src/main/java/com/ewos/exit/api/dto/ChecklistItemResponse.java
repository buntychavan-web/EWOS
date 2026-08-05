package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceDepartment;
import java.util.UUID;

public record ChecklistItemResponse(
        UUID id, ClearanceDepartment department, String itemName, int sortOrder) {}
