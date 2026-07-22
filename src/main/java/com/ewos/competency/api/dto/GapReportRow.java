package com.ewos.competency.api.dto;

import java.util.UUID;

public record GapReportRow(
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        UUID competencyId,
        String competencyCode,
        int requiredLevel,
        int currentLevel,
        int gap) {}
