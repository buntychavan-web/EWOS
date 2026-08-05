package com.ewos.exit.api.dto;

import java.util.List;
import java.util.UUID;

public record ExitChecklistTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID orgUnitId,
        String name,
        boolean active,
        List<ChecklistItemResponse> items) {}
